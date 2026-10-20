package com.mavan.or.newpost.optimization;

import com.mavan.or.newpost.model.*;

import java.util.*;

/**
 * Exact static New Post optimizer.
 *
 * The model is a path-based capacitated multi-commodity formulation.
 * For every commodity all simple paths are candidates. One path (the direct
 * path) is used as a reference and eliminated from the variable set:
 *
 *   sum(alternativePathFlows) <= demand
 *
 * and the omitted direct-path flow is the residual demand. This removes all
 * commodity equality constraints while preserving split shipments exactly.
 *
 * Integer variables are truck-trip counts. Continuous variables are tonnes.
 * Thus multiple commodities may share one truck capacity pool on an arc.
 *
 * The integer truck-trip problem is solved by an in-project LP-relaxation
 * simplex plus branch-and-bound. No external optimization library is used.
 */
public final class NetworkOptimizer {
    private static final double EPS = 1e-7;

    public NewPostResult solve(NewPostProblem problem) {
        return solve(problem, 2_000_000L);
    }

    public NewPostResult solve(NewPostProblem problem, long nodeLimit) {
        validateSupportedMode(problem);

        List<Commodity> commodities = buildCommodities(problem);
        List<DirectedArc> arcs = buildArcs(problem);
        List<PathCandidate> allPaths = buildPaths(problem, commodities);

        int arcCount = arcs.size();
        int vehicleCount = problem.getVehicleTypes().size();

        Map<ArcKey, Integer> arcIndex = new HashMap<>();
        for (int i = 0; i < arcCount; i++) arcIndex.put(new ArcKey(arcs.get(i).getFrom(), arcs.get(i).getTo()), i);

        int[] directPath = new int[commodities.size()];
        Arrays.fill(directPath, -1);
        for (int pi = 0; pi < allPaths.size(); pi++) {
            PathCandidate p = allPaths.get(pi);
            if (p.nodes.size() == 2) directPath[p.commodityIndex] = pi;
        }
        for (int ci = 0; ci < directPath.length; ci++) {
            if (directPath[ci] < 0) throw new IllegalStateException("Direct path missing for commodity " + ci);
        }

        List<PathVariable> variables = new ArrayList<>();
        double constantHandlingCost = 0.0;

        for (int ci = 0; ci < commodities.size(); ci++) {
            PathCandidate base = allPaths.get(directPath[ci]);
            constantHandlingCost += commodities.get(ci).getDemand() * base.handlingCost;
            for (int pi = 0; pi < allPaths.size(); pi++) {
                PathCandidate candidate = allPaths.get(pi);
                if (candidate.commodityIndex != ci || pi == directPath[ci]) continue;
                if (problem.isDirectOnly() && candidate.nodes.size() != 2) continue;
                variables.add(new PathVariable(ci, pi, candidate, base));
            }
        }

        int pathVarCount = variables.size();
        int yCount = arcCount * vehicleCount;
        int totalVarCount = pathVarCount + yCount;
        MilpModel model = new MilpModel(totalVarCount);

        // Path variables: change from direct reference path to alternative path.
        for (int vi = 0; vi < pathVarCount; vi++) {
            PathVariable pv = variables.get(vi);
            model.setUpperBound(vi, pv.commodityDemand);
            model.setObjective(vi, pv.candidate.handlingCost - pv.base.handlingCost);
        }

        // Each commodity's alternative-path flow cannot exceed its demand.
        for (int ci = 0; ci < commodities.size(); ci++) {
            Map<Integer, Double> terms = new HashMap<>();
            for (int vi = 0; vi < pathVarCount; vi++) if (variables.get(vi).commodityIndex == ci) terms.put(vi, 1.0);
            model.addLessEqual(terms, commodities.get(ci).getDemand());
        }

        // Arc capacity after replacing direct-path flow by alternative flows.
        for (int ai = 0; ai < arcCount; ai++) {
            Map<Integer, Double> terms = new HashMap<>();
            double baseLoad = 0.0;

            for (int ci = 0; ci < commodities.size(); ci++) {
                Commodity c = commodities.get(ci);
                PathCandidate base = allPaths.get(directPath[ci]);
                if (base.usesArc(ai, arcIndex)) baseLoad += c.getDemand();
            }

            for (int vi = 0; vi < pathVarCount; vi++) {
                PathVariable pv = variables.get(vi);
                double delta = (pv.candidate.usesArc(ai, arcIndex) ? 1.0 : 0.0)
                        - (pv.base.usesArc(ai, arcIndex) ? 1.0 : 0.0);
                if (Math.abs(delta) > EPS) terms.put(vi, delta);
            }
            terms.putIfAbsent(pathVarCount + ai * vehicleCount, 0.0);

            for (int vt = 0; vt < vehicleCount; vt++) {
                int y = pathVarCount + ai * vehicleCount + vt;
                model.setInteger(y);
                VehicleType vehicle = problem.getVehicleTypes().get(vt);
                model.setObjective(y, arcs.get(ai).getDistance() * vehicle.getTariffPerKm());
                model.setUpperBound(y, conservativeTripUpperBound(problem, vehicle, commodities));
                double capacity = vehicle.usableCapacity(problem.getLoadingFactor());
                terms.merge(y, -capacity, Double::sum);
            }

            model.addLessEqual(terms, -baseLoad);
        }

        MilpSolver solver = new MilpSolver(model);
        solver.setNodeLimit(nodeLimit);

        double[] incumbent = buildHeuristicIncumbent(problem, commodities, arcs, allPaths, directPath,
                variables, pathVarCount, arcCount, vehicleCount, arcIndex, constantHandlingCost);
        solver.setInitialIncumbent(incumbent);

        MilpSolver.SolveResult solution = solver.solve();
        if (!solution.feasible) {
            return new NewPostResult(NewPostResult.Status.INFEASIBLE,
                    "The exact static network model is infeasible.",
                    Double.POSITIVE_INFINITY, 0, 0, 0, 0,
                    totalDemand(commodities), List.of(), List.of(), List.of(), Map.of(), solution.visitedNodes);
        }

        double[] z = solution.solution;
        List<ShipmentFlow> shipments = reconstructShipments(problem, commodities, allPaths, directPath, variables, z, pathVarCount);

        double[] arcLoad = new double[arcCount];
        for (int ci = 0; ci < commodities.size(); ci++) {
            double directAmount = commodities.get(ci).getDemand();
            for (int vi = 0; vi < pathVarCount; vi++) if (variables.get(vi).commodityIndex == ci) directAmount -= Math.max(0.0, z[vi]);
            if (directAmount > EPS) {
                addPathLoad(allPaths.get(directPath[ci]), directAmount, arcLoad, arcIndex);
            }
        }
        for (int vi = 0; vi < pathVarCount; vi++) addPathLoad(variables.get(vi).candidate, Math.max(0.0, z[vi]), arcLoad, arcIndex);

        List<VehicleAssignment> assignments = new ArrayList<>();
        List<VehicleTrip> trips = new ArrayList<>();
        Map<String, Double> arcTonnage = new LinkedHashMap<>();
        double transportCost = 0.0;
        for (int ai = 0; ai < arcCount; ai++) {
            DirectedArc arc = arcs.get(ai);
            double load = arcLoad[ai];
            arcTonnage.put(key(problem, arc), load);
            double remaining = load;
            for (int vt = 0; vt < vehicleCount; vt++) {
                int y = pathVarCount + ai * vehicleCount + vt;
                int count = (int)Math.round(z[y]);
                if (count == 0) continue;
                VehicleType vehicle = problem.getVehicleTypes().get(vt);
                double usable = vehicle.usableCapacity(problem.getLoadingFactor());
                double assigned = Math.min(remaining, usable * count);
                double cost = count * arc.getDistance() * vehicle.getTariffPerKm();
                transportCost += cost;
                assignments.add(new VehicleAssignment(vt, count, assigned, vehicle.getNominalCapacity(), usable, cost));
                for (int t = 0; t < count; t++) {
                    double loadOnTrip = Math.min(usable, Math.max(0.0, remaining));
                    trips.add(new VehicleTrip(arc.getFrom(), arc.getTo(), vt, loadOnTrip, arc.getDistance(), Double.NaN, Double.NaN,
                            arc.getDistance() * vehicle.getTariffPerKm()));
                    remaining -= loadOnTrip;
                }
            }
            if (remaining > 1e-5) throw new IllegalStateException("Vehicle capacity does not cover " + key(problem, arc));
        }

        double terminalCost = calculateTerminalCost(problem, shipments);
        double totalCost = transportCost + terminalCost;
        verify(problem, commodities, shipments, assignments, totalCost, transportCost, terminalCost);

        NewPostResult.Status status = solution.provenOptimal
                ? NewPostResult.Status.OPTIMAL
                : NewPostResult.Status.FEASIBLE_NOT_PROVEN;
        String message = solution.provenOptimal
                ? "Global optimum for the exact static split-flow/consolidation model."
                : "Feasible solution found, but global optimality was not proven before the MILP node limit.";

        return new NewPostResult(status,
                message,
                totalCost, transportCost, terminalCost, 0, 0,
                totalDemand(commodities), shipments, assignments, trips, arcTonnage, solution.visitedNodes);
    }

    private static void validateSupportedMode(NewPostProblem p) {
        if (p.getOwnedVehicleLimits() != null) throw new UnsupportedOperationException("Finite fleet limits require the scheduling-constrained layer.");
        if (p.isRentalEnabled()) throw new UnsupportedOperationException("Rental requires the scheduling-constrained layer.");
        if (p.getDeliveryDeadline() != null) throw new UnsupportedOperationException("A hard deadline requires the scheduling-constrained layer.");
    }

    private static int conservativeTripUpperBound(NewPostProblem p, VehicleType v, List<Commodity> commodities) {
        double total = totalDemand(commodities);
        return Math.max(1, (int)Math.ceil(total / v.usableCapacity(p.getLoadingFactor())) + 1);
    }

    private static double[] buildHeuristicIncumbent(NewPostProblem p, List<Commodity> commodities, List<DirectedArc> arcs,
                                                    List<PathCandidate> allPaths, int[] directPath,
                                                    List<PathVariable> variables, int pathVarCount, int arcCount, int vehicleCount,
                                                    Map<ArcKey,Integer> arcIndex, double constantHandlingCost) {
        double[] solution = new double[pathVarCount + arcCount * vehicleCount];
        int[] selectedPath = directPath.clone();

        // Greedy local search over direct and one-transshipment paths creates a
        // strong feasible incumbent for branch-and-bound without affecting exactness.
        double currentCost = evaluateSelectedPaths(p, commodities, allPaths, selectedPath, arcs, arcIndex);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int ci = 0; ci < commodities.size(); ci++) {
                int current = selectedPath[ci];
                double best = currentCost;
                int bestPath = current;
                for (int pi = 0; pi < allPaths.size(); pi++) {
                    PathCandidate candidate = allPaths.get(pi);
                    if (candidate.commodityIndex != ci || candidate.nodes.size() > 3 || pi == current) continue;
                    if (p.isDirectOnly() && candidate.nodes.size() != 2) continue;
                    selectedPath[ci] = pi;
                    double candidateCost = evaluateSelectedPaths(p, commodities, allPaths, selectedPath, arcs, arcIndex);
                    if (candidateCost < best - 1e-6) {
                        best = candidateCost;
                        bestPath = pi;
                    }
                }
                selectedPath[ci] = bestPath;
                if (bestPath != current) {
                    currentCost = best;
                    changed = true;
                }
            }
        }

        for (int ci = 0; ci < commodities.size(); ci++) {
            int selected = selectedPath[ci];
            if (selected == directPath[ci]) continue;
            for (int vi = 0; vi < pathVarCount; vi++) if (variables.get(vi).pathIndex == selected) {
                solution[vi] = commodities.get(ci).getDemand();
                break;
            }
        }

        double[] load = selectedArcLoads(p, commodities, allPaths, selectedPath, arcs, arcIndex);
        for (int ai = 0; ai < arcCount; ai++) {
            double q = load[ai];
            if (q <= EPS) continue;
            int[] mix = bestVehicleMix(p, arcs.get(ai), q);
            for (int vt = 0; vt < vehicleCount; vt++) solution[pathVarCount + ai * vehicleCount + vt] = mix[vt];
        }
        return solution;
    }

    private static double evaluateSelectedPaths(NewPostProblem p, List<Commodity> commodities, List<PathCandidate> paths,
                                                int[] selectedPath, List<DirectedArc> arcs, Map<ArcKey,Integer> arcIndex) {
        double[] load = selectedArcLoads(p, commodities, paths, selectedPath, arcs, arcIndex);
        double result = 0.0;
        for (int ci = 0; ci < commodities.size(); ci++) result += commodities.get(ci).getDemand() * paths.get(selectedPath[ci]).handlingCost;
        for (int ai = 0; ai < arcs.size(); ai++) {
            if (load[ai] <= EPS) continue;
            int[] mix = bestVehicleMix(p, arcs.get(ai), load[ai]);
            for (int vt = 0; vt < mix.length; vt++) result += mix[vt] * arcs.get(ai).getDistance() * p.getVehicleTypes().get(vt).getTariffPerKm();
        }
        return result;
    }

    private static double[] selectedArcLoads(NewPostProblem p, List<Commodity> commodities, List<PathCandidate> paths,
                                             int[] selectedPath, List<DirectedArc> arcs, Map<ArcKey,Integer> arcIndex) {
        double[] load = new double[arcs.size()];
        for (int ci = 0; ci < commodities.size(); ci++) addPathLoad(paths.get(selectedPath[ci]), commodities.get(ci).getDemand(), load, arcIndex);
        return load;
    }

    private static int[] bestVehicleMix(NewPostProblem p, DirectedArc arc, double load) {
        int n = p.getVehicleTypes().size();
        if (n == 0) throw new IllegalStateException("No vehicle types");
        if (n > 8) throw new IllegalArgumentException("At most 8 vehicle types are supported by the exact mix enumerator");

        // The documented model has three vehicle types. Enumerate all counts
        // of the first n-1 types; the last type is chosen minimally to cover
        // the remaining capacity. This is exact and avoids a 3-dimensional
        // Cartesian product over the last type.
        int[] best = null;
        double bestCost = Double.POSITIVE_INFINITY;
        int[] counts = new int[n];
        enumerateVehicleCounts(p, arc, load, 0, counts, new double[]{bestCost}, new int[][]{null});
        // Re-run with a compact explicit recursion that returns its best result.
        MixSearch search = new MixSearch(p, arc, load);
        search.search(0, counts, 0.0, 0.0);
        best = search.best;
        if (best == null) throw new IllegalStateException("No vehicle mix covers load " + load);
        return best;
    }

    private static void enumerateVehicleCounts(NewPostProblem p, DirectedArc arc, double load, int type, int[] counts, double[] bestCost, int[][] best) {
        // Kept only as a small helper for compatibility with the first implementation.
    }

    private static final class MixSearch {
        final NewPostProblem p;
        final DirectedArc arc;
        final double load;
        int[] best;
        double bestCost = Double.POSITIVE_INFINITY;

        MixSearch(NewPostProblem p, DirectedArc arc, double load) { this.p = p; this.arc = arc; this.load = load; }

        void search(int type, int[] counts, double capacity, double cost) {
            int n = counts.length;
            if (type == n - 1) {
                VehicleType v = p.getVehicleTypes().get(type);
                double remaining = Math.max(0.0, load - capacity);
                int needed = remaining <= EPS ? 0 : (int)Math.ceil(remaining / v.usableCapacity(p.getLoadingFactor()) - EPS);
                counts[type] = needed;
                double finalCapacity = capacity + needed * v.usableCapacity(p.getLoadingFactor());
                double finalCost = cost + needed * arc.getDistance() * v.getTariffPerKm();
                if (finalCapacity + EPS >= load && finalCost < bestCost - EPS) {
                    bestCost = finalCost;
                    best = counts.clone();
                }
                return;
            }

            VehicleType v = p.getVehicleTypes().get(type);
            double cap = v.usableCapacity(p.getLoadingFactor());
            double tripCost = arc.getDistance() * v.getTariffPerKm();
            int max = (int)Math.ceil(load / Math.max(EPS, cap)) + 1;
            for (int count = 0; count <= max; count++) {
                double newCost = cost + count * tripCost;
                if (newCost >= bestCost - EPS) break;
                counts[type] = count;
                search(type + 1, counts, capacity + count * cap, newCost);
            }
            counts[type] = 0;
        }
    }

    private static List<ShipmentFlow> reconstructShipments(NewPostProblem p, List<Commodity> commodities,
                                                            List<PathCandidate> allPaths, int[] directPath,
                                                            List<PathVariable> variables, double[] z, int pathVarCount) {
        List<ShipmentFlow> result = new ArrayList<>();
        for (int ci = 0; ci < commodities.size(); ci++) {
            Commodity c = commodities.get(ci);
            double alternative = 0.0;
            for (int vi = 0; vi < pathVarCount; vi++) {
                if (variables.get(vi).commodityIndex != ci) continue;
                double q = Math.max(0.0, z[vi]);
                alternative += q;
                if (q > EPS) {
                    PathCandidate pth = variables.get(vi).candidate;
                    result.add(new ShipmentFlow(c.getOrigin(), c.getDestination(), q, pth.nodes));
                }
            }
            double direct = c.getDemand() - alternative;
            if (direct > EPS) {
                result.add(new ShipmentFlow(c.getOrigin(), c.getDestination(), direct, allPaths.get(directPath[ci]).nodes));
            }
        }
        return result;
    }

    private static void addPathLoad(PathCandidate path, double amount, double[] loads, Map<ArcKey,Integer> arcIndex) {
        if (amount <= EPS) return;
        for (int k = 0; k < path.nodes.size() - 1; k++) {
            Integer ai = arcIndex.get(new ArcKey(path.nodes.get(k), path.nodes.get(k + 1)));
            if (ai == null) throw new IllegalStateException("Path contains unknown arc");
            loads[ai] += amount;
        }
    }

    private static double calculateTerminalCost(NewPostProblem p, List<ShipmentFlow> shipments) {
        double[] handling = p.getTerminalHandlingCostPerTon();
        double cost=0;
        for(ShipmentFlow s:shipments) for(int city:s.getPath()) cost += s.getTonnage()*handling[city];
        return cost;
    }

    private static void verify(NewPostProblem p, List<Commodity> commodities, List<ShipmentFlow> shipments,
                               List<VehicleAssignment> assignments, double totalCost, double transportCost, double terminalCost) {
        for(Commodity c:commodities){
            double q=0; for(ShipmentFlow s:shipments) if(s.getOrigin()==c.getOrigin()&&s.getDestination()==c.getDestination()) q+=s.getTonnage();
            if(Math.abs(q-c.getDemand())>1e-5) throw new IllegalStateException("Commodity balance failed");
        }
        if(Math.abs(totalCost-transportCost-terminalCost)>1e-5) throw new IllegalStateException("Cost decomposition failed");
        for(VehicleAssignment a:assignments) if(a.getLoadedTonnage()>a.getUsableCapacity()*a.getTripCount()+1e-5) throw new IllegalStateException("Vehicle capacity failed");
    }

    private static double totalDemand(List<Commodity> c){ return c.stream().mapToDouble(Commodity::getDemand).sum(); }
    private static String key(NewPostProblem p, DirectedArc a){ return p.getLocations().get(a.getFrom())+" -> "+p.getLocations().get(a.getTo()); }

    private static List<Commodity> buildCommodities(NewPostProblem p){
        List<Commodity> r=new ArrayList<>(); double[][] d=p.getDemand();
        for(int i=0;i<d.length;i++) for(int j=0;j<d.length;j++) if(i!=j&&d[i][j]>EPS) r.add(new Commodity(i,j,d[i][j]));
        return r;
    }
    private static List<DirectedArc> buildArcs(NewPostProblem p){
        List<DirectedArc> r=new ArrayList<>(); double[][] d=p.getDistance();
        for(int i=0;i<d.length;i++) for(int j=0;j<d.length;j++) if(i!=j) r.add(new DirectedArc(i,j,d[i][j]));
        return r;
    }
    private static List<PathCandidate> buildPaths(NewPostProblem p,List<Commodity> cs){
        List<PathCandidate> r=new ArrayList<>(); SimplePathGenerator g=new SimplePathGenerator(p.locationCount()); double[] h=p.getTerminalHandlingCostPerTon();
        for(int ci=0;ci<cs.size();ci++) for(List<Integer> nodes:g.generate(cs.get(ci).getOrigin(),cs.get(ci).getDestination())){
            double hc=0; for(int city:nodes) hc+=h[city]; r.add(new PathCandidate(ci,cs.get(ci).getOrigin(),cs.get(ci).getDestination(),cs.get(ci).getDemand(),nodes,hc));
        }
        return r;
    }

    private static final class PathVariable{
        final int commodityIndex,pathIndex; final PathCandidate candidate,base; final double commodityDemand;
        PathVariable(int ci,int pi,PathCandidate candidate,PathCandidate base){this.commodityIndex=ci;this.pathIndex=pi;this.candidate=candidate;this.base=base;this.commodityDemand=candidate.commodityDemand;}
    }
    private static final class ArcKey{
        final int from,to; ArcKey(int from,int to){this.from=from;this.to=to;}
        public boolean equals(Object o){return o instanceof ArcKey a&&a.from==from&&a.to==to;}
        public int hashCode(){return 31*from+to;}
    }

    private static final class PathCandidate{
        final int commodityIndex,origin,destination; final double commodityDemand,handlingCost; final List<Integer> nodes;
        PathCandidate(int ci,int o,int d,double demand,List<Integer> nodes,double hc){this.commodityIndex=ci;this.origin=o;this.destination=d;this.commodityDemand=demand;this.nodes=List.copyOf(nodes);this.handlingCost=hc;}
        boolean usesArc(int ai,Map<ArcKey,Integer> map){ for(int k=0;k<nodes.size()-1;k++){Integer x=map.get(new ArcKey(nodes.get(k),nodes.get(k+1)));if(Objects.equals(x,ai))return true;} return false; }
    }
}
