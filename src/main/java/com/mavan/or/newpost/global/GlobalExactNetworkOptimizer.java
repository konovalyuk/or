package com.mavan.or.newpost.global;

import com.mavan.or.newpost.model.NewPostProblem;
import com.mavan.or.newpost.model.VehicleType;
import com.mavan.or.newpost.model.ShipmentFlow;
import com.mavan.or.newpost.optimization.SparsePublicMilpEngine;
import com.mavan.or.newpost.scheduling.ScheduledVehicleTrip;
import com.mavan.or.newpost.scheduling.ShipmentLegSchedule;
import com.mavan.or.newpost.scheduling.TerminalOperation;

import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Joint exact route-and-schedule optimizer.
 *
 * Model:
 *  - continuous commodity flow on a time-expanded directed network;
 *  - terminal processing variables per commodity/city/time;
 *  - integer owned and rental vehicle trips per arc/type/time;
 *  - shared vehicle capacity => consolidation;
 *  - finite simultaneous fleet availability;
 *  - shared terminal capacity per slot;
 *  - hard delivery deadline;
 *  - objective = transport + rental + terminal handling costs.
 *
 * It does NOT replace NetworkOptimizer or ExactScheduleOptimizer. They remain
 * separate public layers. This class is the global layer that jointly decides
 * routing and schedule instead of fixing the routing first.
 *
 * Exactness is with respect to the configured discrete-time model and is
 * reported only after the in-project MILP branch-and-bound exhausts the tree.
 */
public final class GlobalExactNetworkOptimizer {
    private static final double EPS = 1e-7;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    public GlobalExactNetworkResult solve(NewPostProblem problem) {
        return solve(problem, GlobalExactSettings.defaults());
    }

    /**
     * Builds the exact sparse joint model without entering branch-and-bound.
     * Useful for diagnostics and for verifying that a full time-expanded
     * instance is accepted by the sparse backend before starting a long solve.
     */
    public ModelStatistics inspectModel(NewPostProblem problem) {
        Objects.requireNonNull(problem, "problem");
        TimeGrid grid = TimeGrid.from(problem);
        List<Commodity> commodities = commodities(problem);
        List<Arc> arcs = arcs(problem, grid);
        Variables vars = Variables.build(problem, commodities, arcs, grid);
        Model model = buildModel(problem, commodities, arcs, grid, vars);
        return new ModelStatistics(
                vars.variableCount,
                model.constraints.size(),
                model.nonZeroCount,
                grid.horizonSlots,
                grid.periodHours
        );
    }

    public GlobalExactNetworkResult solve(NewPostProblem problem, GlobalExactSettings settings) {
        Objects.requireNonNull(problem, "problem");
        Objects.requireNonNull(settings, "settings");

        TimeGrid grid = TimeGrid.from(problem);
        List<Commodity> commodities = commodities(problem);
        List<Arc> arcs = arcs(problem, grid);
        int estimatedVariables = estimateVariableCount(problem, commodities, arcs, grid);
        int estimatedConstraints = estimateConstraintCount(problem, commodities, arcs, grid);

        if (estimatedVariables > settings.getMaxVariables()
                || estimatedConstraints > settings.getMaxConstraints()) {
            return tooLarge(problem, grid, estimatedVariables, estimatedConstraints, settings,
                    "sparse model dimension guard");
        }

        Variables vars = Variables.build(problem, commodities, arcs, grid);
        Model model = buildModel(problem, commodities, arcs, grid, vars);
        if (model.nonZeroCount > settings.getMaxSparseNonZeros()) {
            return tooLarge(problem, grid, vars.variableCount, model.constraints.size(), settings,
                    "sparse nonzero-coefficient guard: " + model.nonZeroCount + " > "
                            + settings.getMaxSparseNonZeros());
        }

        // The incumbent is only an upper bound. It does not constrain the
        // global model or affect correctness, but it is essential for a
        // responsive branch-and-bound search on the time-expanded model.
        double[] incumbent = buildHeuristicIncumbent(problem, commodities, vars, arcs, grid, model);

        SparsePublicMilpEngine.SolveResult solved = SparsePublicMilpEngine.solve(
                vars.variableCount,
                model.objective,
                model.integer,
                model.upperBounds,
                model.constraints,
                incumbent,
                settings.getNodeLimit()
        );

        if (!solved.feasible()) {
            GlobalExactNetworkResult.Status status = solved.visitedNodes() >= settings.getNodeLimit()
                    ? GlobalExactNetworkResult.Status.SEARCH_LIMIT_REACHED_WITHOUT_FEASIBLE
                    : GlobalExactNetworkResult.Status.INFEASIBLE;
            String message = status == GlobalExactNetworkResult.Status.INFEASIBLE
                    ? "The joint route-and-schedule model is infeasible."
                    : "The search limit was reached before a feasible joint route-and-schedule solution was found.";
            return emptyResult(status, message, grid, vars.variableCount,
                    model.constraints.size(), solved.visitedNodes(), totalDemand(problem));
        }

        Reconstructed r = reconstruct(problem, commodities, arcs, grid, vars, solved.solution());
        GlobalExactNetworkResult.Status status = solved.provenOptimal()
                ? GlobalExactNetworkResult.Status.OPTIMAL
                : GlobalExactNetworkResult.Status.FEASIBLE_NOT_PROVEN;
        String message = solved.provenOptimal()
                ? "Global route-and-schedule optimum proven for the configured discrete time model."
                : "A feasible global route-and-schedule solution was found, but optimality was not proven before the node limit.";

        return new GlobalExactNetworkResult(
                status,
                message,
                solved.provenOptimal(),
                r.totalCost,
                r.transportCost,
                r.terminalCost,
                r.rentalCost,
                r.activationCost,
                r.totalTonnage,
                r.completionHour,
                r.completionSlot,
                grid.horizonSlots,
                grid.periodHours,
                r.routes,
                r.legSchedules,
                r.vehicleTrips,
                r.terminalOperations,
                r.arcTonnage,
                solved.visitedNodes(),
                vars.variableCount,
                model.constraints.size()
        );
    }


    private static double[] buildHeuristicIncumbent(
            NewPostProblem problem,
            List<Commodity> commodities,
            Variables vars,
            List<Arc> arcs,
            TimeGrid grid,
            Model model
    ) {
        // Direct-only seed that respects terminal capacity slot by slot. It is
        // only an upper bound for branch-and-bound and is discarded unless it
        // satisfies every constraint of the model.
        try {
            int h = grid.horizonSlots;
            int n = problem.locationCount();
            double[] incumbent = new double[vars.variableCount];
            double[][] terminalLeft = new double[n][h];
            for (int city = 0; city < n; city++) {
                Arrays.fill(terminalLeft[city], problem.getTerminalCapacityPerHour()[city] * grid.periodHours);
            }

            for (Commodity commodity : commodities) {
                Arc arc = null;
                for (Arc candidate : arcs) {
                    if (candidate.from == commodity.origin && candidate.to == commodity.destination) arc = candidate;
                }
                if (arc == null) return null;

                double[] originProcess = allocate(terminalLeft[commodity.origin], 0, h, commodity.demand);
                if (originProcess == null) return null;
                int lastOrigin = lastPositive(originProcess);
                int departure = lastOrigin + 1;
                if (!arc.canFinishWithinHorizon(departure, h)) return null;
                int arrival = departure + arc.travelSlots;
                double[] destinationProcess = allocate(terminalLeft[commodity.destination], arrival,
                        Math.min(h, grid.deadlineSlot), commodity.demand);
                if (destinationProcess == null) return null;

                double waitingOrigin = commodity.demand;
                double readyOrigin = 0.0;
                double waitingDestination = 0.0;
                for (int t = 0; t < h; t++) {
                    waitingOrigin -= originProcess[t];
                    readyOrigin += originProcess[t];
                    if (t + 1 == departure) readyOrigin -= commodity.demand;
                    if (t == arrival) waitingDestination += commodity.demand;
                    waitingDestination -= destinationProcess[t];
                    incumbent[vars.process(commodity.index, commodity.origin, t)] = originProcess[t];
                    incumbent[vars.process(commodity.index, commodity.destination, t)] = destinationProcess[t];
                    incumbent[vars.waiting(commodity.index, commodity.origin, t)] = clean(waitingOrigin);
                    incumbent[vars.ready(commodity.index, commodity.origin, t)] = clean(readyOrigin);
                    incumbent[vars.waiting(commodity.index, commodity.destination, t)] = clean(waitingDestination);
                }
                incumbent[vars.depart(commodity.index, arc.index, departure)] = commodity.demand;
            }

            for (Arc arc : arcs) {
                for (int t = 0; t < h; t++) {
                    double load = 0.0;
                    for (Commodity commodity : commodities) load += incumbent[vars.depart(commodity.index, arc.index, t)];
                    if (load <= EPS) continue;
                    int bestType = -1;
                    double bestCost = Double.POSITIVE_INFINITY;
                    int bestTrips = 0;
                    for (int vt = 0; vt < problem.getVehicleTypes().size(); vt++) {
                        VehicleType vehicle = problem.getVehicleTypes().get(vt);
                        int trips = (int) Math.ceil(load / vehicle.usableCapacity(problem.getLoadingFactor()) - EPS);
                        double cost = trips * arc.distance * vehicle.getTariffPerKm();
                        if (cost < bestCost) {
                            bestCost = cost;
                            bestType = vt;
                            bestTrips = trips;
                        }
                    }
                    int[] owned = problem.getOwnedVehicleLimits();
                    boolean rent = owned != null && problem.isRentalEnabled() && owned[bestType] < bestTrips;
                    incumbent[rent ? vars.rentalTrip(arc.index, bestType, t) : vars.ownedTrip(arc.index, bestType, t)] = bestTrips;
                    int routeOpen = vars.routeOpen(arc.index, t);
                    if (routeOpen >= 0) incumbent[routeOpen] = 1.0;
                    int vehicleOpen = vars.vehicleOpen(bestType, t);
                    if (vehicleOpen >= 0) incumbent[vehicleOpen] = 1.0;
                }
            }
            return isFeasible(model, incumbent) ? incumbent : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static double[] allocate(double[] left, int from, int to, double amount) {
        double[] used = new double[left.length];
        double remaining = amount;
        for (int t = Math.max(0, from); t < to && remaining > EPS; t++) {
            double q = Math.min(left[t], remaining);
            if (q <= EPS) continue;
            used[t] = q;
            remaining -= q;
        }
        if (remaining > EPS) return null;
        for (int t = 0; t < left.length; t++) left[t] -= used[t];
        return used;
    }

    private static int lastPositive(double[] values) {
        for (int t = values.length - 1; t >= 0; t--) if (values[t] > EPS) return t;
        return -1;
    }

    private static boolean isFeasible(Model model, double[] x) {
        for (int i = 0; i < x.length; i++) {
            if (x[i] < -1e-7 || x[i] > model.upperBounds[i] + 1e-7) return false;
            if (model.integer[i] && Math.abs(x[i] - Math.rint(x[i])) > 1e-6) return false;
        }
        for (SparsePublicMilpEngine.Constraint constraint : model.constraints) {
            double lhs = 0.0;
            for (int p = 0; p < constraint.indexes().length; p++) {
                lhs += constraint.coefficients()[p] * x[constraint.indexes()[p]];
            }
            if (lhs > constraint.rhs() + 1e-6) return false;
        }
        return true;
    }

    private static int commodityIndex(List<Commodity> commodities, int origin, int destination) {
        for (Commodity c : commodities) {
            if (c.origin == origin && c.destination == destination) return c.index;
        }
        return -1;
    }

    private static int estimateVariableCount(
            NewPostProblem problem, List<Commodity> commodities, List<Arc> arcs, TimeGrid grid) {
        long c = commodities.size();
        long h = grid.horizonSlots;
        long n = problem.locationCount();
        long v = problem.getVehicleTypes().size();
        long count = 3L * c * n * h + c * arcs.size() * h + 2L * arcs.size() * v * h;
        if (problem.getRouteActivationCost() != null) count += (long) arcs.size() * h;
        if (problem.getVehicleActivationCost() != null) count += v * h;
        return checkedSize(count);
    }

    private static int estimateConstraintCount(
            NewPostProblem problem, List<Commodity> commodities, List<Arc> arcs, TimeGrid grid) {
        long c = commodities.size();
        long h = grid.horizonSlots;
        long n = problem.locationCount();
        long v = problem.getVehicleTypes().size();
        long count = 2L * c * n * h; // waiting balance
        count += 2L * c * (n - 1) * h; // ready balance
        count += 2L * c; // destination equality
        count += n * h; // shared terminal capacity
        count += (long) arcs.size() * h; // shared vehicle capacity
        if (problem.getRouteActivationCost() != null) count += (long) arcs.size() * v * h;
        if (problem.getVehicleActivationCost() != null) count += v * h;
        if (problem.getOwnedVehicleLimits() != null) {
            count += (long) problem.getVehicleTypes().size() * h;
        }
        if (problem.isRentalEnabled() && problem.getRentalLimits() != null) {
            count += (long) problem.getVehicleTypes().size() * h;
        }
        return checkedSize(count);
    }

    private static int checkedSize(long value) {
        if (value > Integer.MAX_VALUE) throw new IllegalArgumentException("Global model is too large");
        return (int) value;
    }

    private static GlobalExactNetworkResult tooLarge(
            NewPostProblem problem,
            TimeGrid grid,
            int variableCount,
            int constraintCount,
            GlobalExactSettings settings,
            String reason
    ) {
        String message = "The sparse joint exact model exceeds the configured safety limits: " + reason
                + ". No mathematical simplification was applied.";
        return emptyResult(GlobalExactNetworkResult.Status.MODEL_TOO_LARGE, message, grid,
                variableCount, constraintCount, 0, totalDemand(problem));
    }

    private static GlobalExactNetworkResult emptyResult(
            GlobalExactNetworkResult.Status status,
            String message,
            TimeGrid grid,
            int variables,
            int constraints,
            long nodes,
            double tonnage
    ) {
        return new GlobalExactNetworkResult(
                status, message, false,
                Double.POSITIVE_INFINITY, 0.0, 0.0, 0.0,
                0.0,
                tonnage, Double.POSITIVE_INFINITY, -1,
                grid.horizonSlots, grid.periodHours,
                List.of(), List.of(), List.of(), List.of(), Map.of(),
                nodes, variables, constraints
        );
    }

    private static Model buildModel(
            NewPostProblem problem,
            List<Commodity> commodities,
            List<Arc> arcs,
            TimeGrid grid,
            Variables vars
    ) {
        int n = vars.variableCount;
        double[] objective = new double[n];
        boolean[] integer = new boolean[n];
        double[] upperBounds = new double[n];
        Arrays.fill(upperBounds, Double.POSITIVE_INFINITY);
        List<SparsePublicMilpEngine.Constraint> constraints = new ArrayList<>();
        long nonZeroCount = 0L;

        double[] handling = problem.getTerminalHandlingCostPerTon();
        double[] terminalCapacity = problem.getTerminalCapacityPerHour();
        List<VehicleType> vehicles = problem.getVehicleTypes();
        double loadingFactor = problem.getLoadingFactor();
        double[][] routeActivation = problem.getRouteActivationCost();
        double[] vehicleActivation = problem.getVehicleActivationCost();

        // 1) Commodity flow balance with two inventory states per city:
        //
        //   waiting[t] = waiting[t-1] + arrivals[t] + supply[t] - processing[t]
        //   ready[t]   = ready[t-1]   + processing[t] - departures[t+1]
        //
        // Freight can leave a city only after it was processed there, so the
        // origin, every transshipment city and the destination are all
        // handled (and charged) and every visit consumes terminal capacity.
        // At the destination processed freight leaves the network.
        for (int c = 0; c < commodities.size(); c++) {
            Commodity commodity = commodities.get(c);
            double demand = commodity.demand;

            for (int city = 0; city < problem.locationCount(); city++) {
                for (int t = 0; t < grid.horizonSlots; t++) {
                    Map<Integer, Double> waiting = new HashMap<>();
                    addTerm(waiting, vars.waiting(c, city, t), 1.0);
                    if (t > 0) addTerm(waiting, vars.waiting(c, city, t - 1), -1.0);
                    addTerm(waiting, vars.process(c, city, t), 1.0);
                    for (Arc arc : arcs) {
                        if (arc.to == city && arc.travelSlots <= t) {
                            addTerm(waiting, vars.depart(c, arc.index, t - arc.travelSlots), -1.0);
                        }
                    }
                    double rhs = (city == commodity.origin && t == 0) ? demand : 0.0;
                    addSparseEquality(constraints, waiting, rhs);
                    nonZeroCount += 2L * nonZeroCount(waiting);

                    if (city == commodity.destination) continue;
                    Map<Integer, Double> ready = new HashMap<>();
                    addTerm(ready, vars.ready(c, city, t), 1.0);
                    if (t > 0) addTerm(ready, vars.ready(c, city, t - 1), -1.0);
                    addTerm(ready, vars.process(c, city, t), -1.0);
                    if (t + 1 < grid.horizonSlots) {
                        for (Arc arc : arcs) {
                            if (arc.from == city) addTerm(ready, vars.depart(c, arc.index, t + 1), 1.0);
                        }
                    }
                    addSparseEquality(constraints, ready, 0.0);
                    nonZeroCount += 2L * nonZeroCount(ready);
                }
            }

            Map<Integer, Double> destination = new HashMap<>();
            for (int t = 0; t < grid.horizonSlots; t++) {
                if (grid.finalProcessingAllowed(t)) {
                    addTerm(destination, vars.process(c, commodity.destination, t), 1.0);
                } else {
                    upperBounds[vars.process(c, commodity.destination, t)] = 0.0;
                }
            }
            addSparseEquality(constraints, destination, demand);
            nonZeroCount += 2L * nonZeroCount(destination);

            for (int city = 0; city < problem.locationCount(); city++) {
                boolean allowedProcessing = !problem.isDirectOnly()
                        || city == commodity.origin
                        || city == commodity.destination;
                for (int t = 0; t < grid.horizonSlots; t++) {
                    upperBounds[vars.process(c, city, t)] = allowedProcessing ? demand : 0.0;
                    objective[vars.process(c, city, t)] += handling[city];
                }
            }

            for (int t = 0; t < grid.horizonSlots; t++) {
                upperBounds[vars.ready(c, commodity.destination, t)] = 0.0;
            }
            for (Arc arc : arcs) {
                boolean allowedArc = arc.from != commodity.destination
                        && (!problem.isDirectOnly()
                        || (arc.from == commodity.origin && arc.to == commodity.destination));
                for (int t = 0; t < grid.horizonSlots; t++) {
                    upperBounds[vars.depart(c, arc.index, t)] =
                            allowedArc && arc.canFinishWithinHorizon(t, grid.horizonSlots)
                                    ? demand : 0.0;
                }
            }
            for (Arc arc : arcs) {
                upperBounds[vars.depart(c, arc.index, 0)] = 0.0;
            }
        }

        // 2) Shared terminal capacity per time slot.
        for (int city = 0; city < problem.locationCount(); city++) {
            double capacityPerSlot = terminalCapacity[city] * grid.periodHours;
            for (int t = 0; t < grid.horizonSlots; t++) {
                Map<Integer, Double> terms = new HashMap<>();
                for (int c = 0; c < commodities.size(); c++) {
                    addTerm(terms, vars.process(c, city, t), 1.0);
                }
                constraints.add(toSparseConstraint(terms, capacityPerSlot));
                nonZeroCount += nonZeroCount(terms);
            }
        }

        // 3) Shared physical capacity on every directed arc/departure slot.
        for (Arc arc : arcs) {
            for (int t = 0; t < grid.horizonSlots; t++) {
                Map<Integer, Double> terms = new HashMap<>();
                for (int c = 0; c < commodities.size(); c++) {
                    addTerm(terms, vars.depart(c, arc.index, t), 1.0);
                }
                for (int vt = 0; vt < vehicles.size(); vt++) {
                    double usable = vehicles.get(vt).usableCapacity(loadingFactor);
                    int owned = vars.ownedTrip(arc.index, vt, t);
                    int rental = vars.rentalTrip(arc.index, vt, t);
                    addTerm(terms, owned, -usable);
                    addTerm(terms, rental, -usable);
                    integer[owned] = true;
                    integer[rental] = true;

                    double transport = arc.distance * vehicles.get(vt).getTariffPerKm();
                    objective[owned] += transport;
                    objective[rental] += transport + vehicles.get(vt).getRentalCostPerTrip();

                    upperBounds[owned] = arc.canFinishWithinHorizon(t, grid.horizonSlots)
                            ? arc.maxTrips : 0.0;
                    upperBounds[rental] = problem.isRentalEnabled()
                            && arc.canFinishWithinHorizon(t, grid.horizonSlots)
                            ? arc.maxTrips : 0.0;
                }
                constraints.add(toSparseConstraint(terms, 0.0));
                nonZeroCount += nonZeroCount(terms);
            }
        }

        // 3a) Optional fixed activation charges. Binary variables exist only
        // for positive charges; zero charges cannot change the optimum.
        for (Arc arc : arcs) {
            for (int t = 0; t < grid.horizonSlots; t++) {
                int open = vars.routeOpen(arc.index, t);
                if (open < 0) continue;
                integer[open] = true;
                upperBounds[open] = 1.0;
                objective[open] += routeActivation[arc.from][arc.to];
                for (int vt = 0; vt < vehicles.size(); vt++) {
                    Map<Integer, Double> terms = new HashMap<>();
                    addTerm(terms, vars.ownedTrip(arc.index, vt, t), 1.0);
                    addTerm(terms, vars.rentalTrip(arc.index, vt, t), 1.0);
                    addTerm(terms, open, -arc.maxTrips);
                    constraints.add(toSparseConstraint(terms, 0.0));
                    nonZeroCount += nonZeroCount(terms);
                }
            }
        }
        for (int vt = 0; vt < vehicles.size(); vt++) {
            for (int t = 0; t < grid.horizonSlots; t++) {
                int open = vars.vehicleOpen(vt, t);
                if (open < 0) continue;
                integer[open] = true;
                upperBounds[open] = 1.0;
                objective[open] += vehicleActivation[vt];
                Map<Integer, Double> terms = new HashMap<>();
                for (Arc arc : arcs) {
                    addTerm(terms, vars.ownedTrip(arc.index, vt, t), 1.0);
                    addTerm(terms, vars.rentalTrip(arc.index, vt, t), 1.0);
                }
                addTerm(terms, open, -((double) arcs.size() * maxTrips(arcs)));
                constraints.add(toSparseConstraint(terms, 0.0));
                nonZeroCount += nonZeroCount(terms);
            }
        }

        // 4) Finite owned fleet availability. A truck remains occupied from
        // departure until arrival, so a time slot counts all trips whose
        // [start, finish) interval covers that slot.
        int[] ownedLimits = problem.getOwnedVehicleLimits();
        if (ownedLimits != null) {
            for (int vt = 0; vt < vehicles.size(); vt++) {
                for (int t = 0; t < grid.horizonSlots; t++) {
                    Map<Integer, Double> terms = new HashMap<>();
                    for (Arc arc : arcs) {
                        for (int start = 0; start <= t; start++) {
                            if (start + arc.travelSlots > t) {
                                addTerm(terms, vars.ownedTrip(arc.index, vt, start), 1.0);
                            }
                        }
                    }
                    constraints.add(toSparseConstraint(terms, ownedLimits[vt]));
                    nonZeroCount += nonZeroCount(terms);
                }
            }
        }

        // 5) Rental availability.
        int[] rentalLimits = problem.getRentalLimits();
        if (problem.isRentalEnabled() && rentalLimits != null) {
            for (int vt = 0; vt < vehicles.size(); vt++) {
                for (int t = 0; t < grid.horizonSlots; t++) {
                    Map<Integer, Double> terms = new HashMap<>();
                    for (Arc arc : arcs) {
                        for (int start = 0; start <= t; start++) {
                            if (start + arc.travelSlots > t) {
                                addTerm(terms, vars.rentalTrip(arc.index, vt, start), 1.0);
                            }
                        }
                    }
                    constraints.add(toSparseConstraint(terms, rentalLimits[vt]));
                    nonZeroCount += nonZeroCount(terms);
                }
            }
        }

        return new Model(
                objective,
                integer,
                upperBounds,
                constraints,
                nonZeroCount
        );
    }

    private static void addTerm(Map<Integer, Double> terms, int index, double coefficient) {
        if (Math.abs(coefficient) <= EPS) return;
        terms.merge(index, coefficient, Double::sum);
        if (Math.abs(terms.get(index)) <= EPS) terms.remove(index);
    }

    private static SparsePublicMilpEngine.Constraint toSparseConstraint(
            Map<Integer, Double> terms,
            double rhs
    ) {
        return SparsePublicMilpEngine.Constraint.fromMap(terms, rhs);
    }

    private static void addSparseEquality(
            List<SparsePublicMilpEngine.Constraint> constraints,
            Map<Integer, Double> terms,
            double rhs
    ) {
        constraints.add(toSparseConstraint(terms, rhs));
        Map<Integer, Double> negated = new HashMap<>();
        for (Map.Entry<Integer, Double> entry : terms.entrySet()) {
            negated.put(entry.getKey(), -entry.getValue());
        }
        constraints.add(toSparseConstraint(negated, -rhs));
    }

    private static int nonZeroCount(Map<Integer, Double> terms) {
        return terms.size();
    }

    private static Reconstructed reconstruct(
            NewPostProblem problem,
            List<Commodity> commodities,
            List<Arc> arcs,
            TimeGrid grid,
            Variables vars,
            double[] x
    ) {
        List<ShipmentLegSchedule> legs = new ArrayList<>();
        List<TerminalOperation> terminalOps = new ArrayList<>();
        List<ScheduledVehicleTrip> trips = new ArrayList<>();
        Map<String, Double> arcTonnage = new LinkedHashMap<>();

        double transportCost = 0.0;
        double rentalCost = 0.0;
        double activationCost = 0.0;
        double terminalCost = 0.0;
        int completionSlot = 0;

        // Scheduled commodity movements.
        int[] legCounters = new int[commodities.size()];
        double[][][][] residual = new double[commodities.size()][arcs.size()][][];
        for (int c = 0; c < commodities.size(); c++) {
            for (Arc arc : arcs) {
                residual[c][arc.index] = new double[1][grid.horizonSlots];
                for (int t = 0; t < grid.horizonSlots; t++) {
                    double q = clean(x[vars.depart(c, arc.index, t)]);
                    residual[c][arc.index][0][t] = q;
                    if (q > EPS) {
                        String key = problem.getLocations().get(arc.from) + " -> " + problem.getLocations().get(arc.to);
                        arcTonnage.merge(key, q, Double::sum);
                        legs.add(new ShipmentLegSchedule(
                                c,
                                legCounters[c]++,
                                arc.from,
                                arc.to,
                                q,
                                t,
                                t + arc.travelSlots,
                                t * grid.periodHours,
                                (t + arc.travelSlots) * grid.periodHours
                        ));
                    }
                }
            }
        }

        // Terminal operations.
        int[] visitCounter = new int[commodities.size()];
        for (int c = 0; c < commodities.size(); c++) {
            for (int city = 0; city < problem.locationCount(); city++) {
                for (int t = 0; t < grid.horizonSlots; t++) {
                    double q = clean(x[vars.process(c, city, t)]);
                    if (q <= EPS) continue;
                    double cost = q * problem.getTerminalHandlingCostPerTon()[city];
                    terminalOps.add(new TerminalOperation(
                            c, city, visitCounter[c]++, q,
                            t, t + 1,
                            t * grid.periodHours,
                            (t + 1) * grid.periodHours,
                            cost
                    ));
                    terminalCost += cost;
                    if (city == commodities.get(c).destination) {
                        completionSlot = Math.max(completionSlot, t + 1);
                    }
                }
            }
        }

        // Physical truck trips; each departure slot can consolidate several commodities.
        for (Arc arc : arcs) {
            for (int t = 0; t < grid.horizonSlots; t++) {
                double load = 0.0;
                for (int c = 0; c < commodities.size(); c++) {
                    load += clean(x[vars.depart(c, arc.index, t)]);
                }
                if (load <= EPS) continue;

                double remaining = load;
                for (int vt = 0; vt < problem.getVehicleTypes().size(); vt++) {
                    VehicleType vehicle = problem.getVehicleTypes().get(vt);
                    double usable = vehicle.usableCapacity(problem.getLoadingFactor());
                    int ownedCount = (int) Math.round(clean(x[vars.ownedTrip(arc.index, vt, t)]));
                    int rentalCount = (int) Math.round(clean(x[vars.rentalTrip(arc.index, vt, t)]));
                    double transportPerTrip = arc.distance * vehicle.getTariffPerKm();
                    double rentalPerTrip = vehicle.getRentalCostPerTrip();

                    for (int i = 0; i < ownedCount; i++) {
                        double tripLoad = Math.min(usable, Math.max(0.0, remaining));
                        trips.add(new ScheduledVehicleTrip(
                                arc.from, arc.to, vt, false, tripLoad,
                                vehicle.getNominalCapacity(), usable, arc.distance,
                                t, t + arc.travelSlots,
                                t * grid.periodHours,
                                (t + arc.travelSlots) * grid.periodHours,
                                transportPerTrip, 0.0
                        ));
                        transportCost += transportPerTrip;
                        remaining -= tripLoad;
                    }
                    for (int i = 0; i < rentalCount; i++) {
                        double tripLoad = Math.min(usable, Math.max(0.0, remaining));
                        trips.add(new ScheduledVehicleTrip(
                                arc.from, arc.to, vt, true, tripLoad,
                                vehicle.getNominalCapacity(), usable, arc.distance,
                                t, t + arc.travelSlots,
                                t * grid.periodHours,
                                (t + arc.travelSlots) * grid.periodHours,
                                transportPerTrip, rentalPerTrip
                        ));
                        transportCost += transportPerTrip;
                        rentalCost += rentalPerTrip;
                        remaining -= tripLoad;
                    }
                }
                if (remaining > 1e-5) {
                    throw new IllegalStateException("Trip reconstruction failed for "
                            + arc.from + "->" + arc.to + " at slot " + t
                            + ": remaining " + remaining);
                }
            }
        }
        for (Arc arc : arcs) {
            for (int t = 0; t < grid.horizonSlots; t++) {
                int open = vars.routeOpen(arc.index, t);
                if (open >= 0 && clean(x[open]) > 0.5) {
                    activationCost += problem.getRouteActivationCost()[arc.from][arc.to];
                }
            }
        }
        double[] vehicleActivation = problem.getVehicleActivationCost();
        for (int vt = 0; vt < problem.getVehicleTypes().size(); vt++) {
            for (int t = 0; t < grid.horizonSlots; t++) {
                int open = vars.vehicleOpen(vt, t);
                if (open >= 0 && clean(x[open]) > 0.5) activationCost += vehicleActivation[vt];
            }
        }

        List<GlobalShipmentRoute> routes = decomposeRoutes(
                problem, commodities, arcs, grid, vars, x
        );

        double totalCost = transportCost + rentalCost + terminalCost + activationCost;
        double totalTonnage = totalDemand(problem);

        verifyGlobalSolution(
                problem, commodities, arcs, grid, vars, x,
                totalCost, transportCost, rentalCost, terminalCost + activationCost
        );

        return new Reconstructed(
                totalCost,
                transportCost,
                terminalCost,
                rentalCost,
                activationCost,
                totalTonnage,
                completionSlot * grid.periodHours,
                completionSlot,
                routes,
                legs,
                trips,
                terminalOps,
                arcTonnage
        );
    }

    private static List<GlobalShipmentRoute> decomposeRoutes(
            NewPostProblem problem,
            List<Commodity> commodities,
            List<Arc> arcs,
            TimeGrid grid,
            Variables vars,
            double[] solution
    ) {
        List<GlobalShipmentRoute> result = new ArrayList<>();

        for (int c = 0; c < commodities.size(); c++) {
            Commodity commodity = commodities.get(c);
            Map<MovementKey, Double> residual = new LinkedHashMap<>();
            for (Arc arc : arcs) {
                for (int t = 0; t < grid.horizonSlots; t++) {
                    double q = clean(solution[vars.depart(c, arc.index, t)]);
                    if (q > EPS) residual.put(new MovementKey(arc.index, t), q);
                }
            }

            while (true) {
                MovementKey first = firstDepartureFrom(residual, arcs, commodity.origin);
                if (first == null) break;

                List<MovementKey> path = new ArrayList<>();
                path.add(first);
                int currentCity = arcs.get(first.arcIndex).to;
                int arrival = first.slot + arcs.get(first.arcIndex).travelSlots;
                double amount = residual.get(first);
                double departureHour = first.slot * grid.periodHours;
                double arrivalHour = arrival * grid.periodHours;
                double distance = arcs.get(first.arcIndex).distance;

                while (currentCity != commodity.destination) {
                    MovementKey next = firstDepartureFromAfter(residual, arcs, currentCity, arrival);
                    if (next == null) {
                        throw new IllegalStateException("Cannot decompose feasible global flow for commodity " + c);
                    }
                    amount = Math.min(amount, residual.get(next));
                    path.add(next);
                    Arc nextArc = arcs.get(next.arcIndex);
                    currentCity = nextArc.to;
                    arrival = next.slot + nextArc.travelSlots;
                    arrivalHour = arrival * grid.periodHours;
                    distance += nextArc.distance;
                }

                for (MovementKey movement : path) {
                    double left = residual.get(movement) - amount;
                    if (left <= EPS) residual.remove(movement);
                    else residual.put(movement, left);
                }

                List<Integer> cityPath = new ArrayList<>();
                cityPath.add(commodity.origin);
                int cursor = commodity.origin;
                for (MovementKey movement : path) {
                    Arc a = arcs.get(movement.arcIndex);
                    if (a.from != cursor) {
                        throw new IllegalStateException("Invalid route decomposition");
                    }
                    cityPath.add(a.to);
                    cursor = a.to;
                }

                result.add(new GlobalShipmentRoute(
                        c,
                        commodity.origin,
                        commodity.destination,
                        amount,
                        cityPath,
                        departureHour,
                        arrivalHour,
                        distance
                ));
            }

            double decomposed = 0.0;
            for (GlobalShipmentRoute route : result) {
                if (route.getCommodityIndex() == c) decomposed += route.getTonnage();
            }
            if (Math.abs(decomposed - commodity.demand) > 1e-5) {
                throw new IllegalStateException("Route decomposition balance failed for commodity "
                        + c + ": expected " + commodity.demand + ", got " + decomposed);
            }
        }

        return result;
    }

    private static MovementKey firstDepartureFrom(
            Map<MovementKey, Double> residual,
            List<Arc> arcs,
            int city
    ) {
        return residual.entrySet().stream()
                .filter(e -> arcs.get(e.getKey().arcIndex).from == city)
                .min(Comparator.comparingInt((Map.Entry<MovementKey, Double> e) -> e.getKey().slot)
                        .thenComparingInt(e -> e.getKey().arcIndex))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private static MovementKey firstDepartureFromAfter(
            Map<MovementKey, Double> residual,
            List<Arc> arcs,
            int city,
            int earliestSlot
    ) {
        return residual.entrySet().stream()
                .filter(e -> {
                    Arc a = arcs.get(e.getKey().arcIndex);
                    return a.from == city && e.getKey().slot >= earliestSlot;
                })
                .min(Comparator.comparingInt((Map.Entry<MovementKey, Double> e) -> e.getKey().slot)
                        .thenComparingInt(e -> e.getKey().arcIndex))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private static void verifyGlobalSolution(
            NewPostProblem problem,
            List<Commodity> commodities,
            List<Arc> arcs,
            TimeGrid grid,
            Variables vars,
            double[] x,
            double totalCost,
            double transportCost,
            double rentalCost,
            double terminalCost
    ) {
        double[][] demand = problem.getDemand();
        for (int c = 0; c < commodities.size(); c++) {
            Commodity commodity = commodities.get(c);
            double delivered = 0.0;
            for (int t = 0; t < grid.deadlineSlot; t++) {
                delivered += clean(x[vars.process(c, commodity.destination, t)]);
            }
            if (Math.abs(delivered - commodity.demand) > 1e-5) {
                throw new IllegalStateException("Global delivery balance failed for commodity " + c);
            }
        }

        for (Arc arc : arcs) {
            for (int t = 0; t < grid.horizonSlots; t++) {
                double load = 0.0;
                for (int c = 0; c < commodities.size(); c++) load += clean(x[vars.depart(c, arc.index, t)]);
                double capacity = 0.0;
                for (int vt = 0; vt < problem.getVehicleTypes().size(); vt++) {
                    VehicleType vehicle = problem.getVehicleTypes().get(vt);
                    capacity += (clean(x[vars.ownedTrip(arc.index, vt, t)])
                            + clean(x[vars.rentalTrip(arc.index, vt, t)]))
                            * vehicle.usableCapacity(problem.getLoadingFactor());
                }
                if (load > capacity + 1e-5) {
                    throw new IllegalStateException("Global vehicle-capacity violation on arc " + arc.index);
                }
            }
        }

        if (Math.abs(totalCost - (transportCost + rentalCost + terminalCost)) > 1e-5) {
            throw new IllegalStateException("Global cost decomposition failed");
        }
    }

    private static List<Commodity> commodities(NewPostProblem problem) {
        double[][] demand = problem.getDemand();
        List<Commodity> result = new ArrayList<>();
        int index = 0;
        for (int i = 0; i < demand.length; i++) {
            for (int j = 0; j < demand[i].length; j++) {
                if (i != j && demand[i][j] > EPS) {
                    result.add(new Commodity(index++, i, j, demand[i][j]));
                }
            }
        }
        return result;
    }

    private static List<Arc> arcs(NewPostProblem problem, TimeGrid grid) {
        double[][] distance = problem.getDistance();
        double totalDemand = totalDemand(problem);
        double minUsableCapacity = problem.getVehicleTypes().stream()
                .mapToDouble(v -> v.usableCapacity(problem.getLoadingFactor()))
                .min()
                .orElseThrow();
        int safeMaxTrips = Math.max(1, (int) Math.ceil(totalDemand / minUsableCapacity + EPS));

        List<Arc> result = new ArrayList<>();
        int index = 0;
        for (int i = 0; i < distance.length; i++) {
            for (int j = 0; j < distance.length; j++) {
                if (i == j) continue;
                if (!(distance[i][j] > 0.0)) continue;
                int slots = travelSlots(distance[i][j], problem.getSpeedKmPerHour(), grid.periodHours);
                result.add(new Arc(index++, i, j, distance[i][j], slots, safeMaxTrips));
            }
        }
        return result;
    }

    private static double totalDemand(NewPostProblem problem) {
        double total = 0.0;
        for (double[] row : problem.getDemand()) for (double v : row) total += v;
        return total;
    }

    private static double clean(double v) { return Math.abs(v) <= EPS ? 0.0 : v; }

    private record Commodity(int index, int origin, int destination, double demand) {}

    private record Arc(int index, int from, int to, double distance, int travelSlots, int maxTrips) {
        boolean canFinishWithinHorizon(int start, int horizon) {
            return start + travelSlots <= horizon;
        }
    }

    private static final class Variables {
        final int variableCount;
        final int[][][] process;
        final int[][][] waiting;
        final int[][][] ready;
        final int[][][] depart;
        final int[][][] ownedTrip;
        final int[][][] rentalTrip;
        final int[][] routeOpen;
        final int[][] vehicleOpen;

        private Variables(int variableCount, int[][][] process, int[][][] waiting, int[][][] ready, int[][][] depart,
                          int[][][] ownedTrip, int[][][] rentalTrip, int[][] routeOpen, int[][] vehicleOpen) {
            this.variableCount = variableCount;
            this.process = process;
            this.waiting = waiting;
            this.ready = ready;
            this.depart = depart;
            this.ownedTrip = ownedTrip;
            this.rentalTrip = rentalTrip;
            this.routeOpen = routeOpen;
            this.vehicleOpen = vehicleOpen;
        }

        static Variables build(NewPostProblem problem, List<Commodity> commodities,
                               List<Arc> arcs, TimeGrid grid) {
            int count = 0;
            int n = problem.locationCount();
            int h = grid.horizonSlots;
            int[][][] process = new int[commodities.size()][n][h];
            int[][][] waiting = new int[commodities.size()][n][h];
            int[][][] ready = new int[commodities.size()][n][h];
            int[][][] depart = new int[commodities.size()][arcs.size()][h];
            for (int c = 0; c < commodities.size(); c++) {
                for (int city = 0; city < n; city++) for (int t = 0; t < h; t++) process[c][city][t] = count++;
                for (int city = 0; city < n; city++) for (int t = 0; t < h; t++) waiting[c][city][t] = count++;
                for (int city = 0; city < n; city++) for (int t = 0; t < h; t++) ready[c][city][t] = count++;
                for (Arc arc : arcs) for (int t = 0; t < h; t++) depart[c][arc.index][t] = count++;
            }

            int vtCount = problem.getVehicleTypes().size();
            int[][][] owned = new int[arcs.size()][vtCount][grid.horizonSlots];
            int[][][] rental = new int[arcs.size()][vtCount][grid.horizonSlots];
            for (Arc arc : arcs) {
                for (int vt = 0; vt < vtCount; vt++) {
                    for (int t = 0; t < grid.horizonSlots; t++) {
                        owned[arc.index][vt][t] = count++;
                        rental[arc.index][vt][t] = count++;
                    }
                }
            }
            double[][] routeCost = problem.getRouteActivationCost();
            int[][] routeOpen = new int[arcs.size()][h];
            for (Arc arc : arcs) {
                boolean needed = routeCost != null && routeCost[arc.from][arc.to] > EPS;
                for (int t = 0; t < h; t++) routeOpen[arc.index][t] = needed ? count++ : -1;
            }
            double[] vehicleCost = problem.getVehicleActivationCost();
            int[][] vehicleOpen = new int[vtCount][h];
            for (int vt = 0; vt < vtCount; vt++) {
                boolean needed = vehicleCost != null && vehicleCost[vt] > EPS;
                for (int t = 0; t < h; t++) vehicleOpen[vt][t] = needed ? count++ : -1;
            }
            return new Variables(count, process, waiting, ready, depart, owned, rental, routeOpen, vehicleOpen);
        }

        int process(int c, int city, int t) { return process[c][city][t]; }
        int waiting(int c, int city, int t) { return waiting[c][city][t]; }
        int ready(int c, int city, int t) { return ready[c][city][t]; }
        int depart(int c, int arc, int t) { return depart[c][arc][t]; }
        int ownedTrip(int arc, int vt, int t) { return ownedTrip[arc][vt][t]; }
        int rentalTrip(int arc, int vt, int t) { return rentalTrip[arc][vt][t]; }
        int routeOpen(int arc, int t) { return routeOpen[arc][t]; }
        int vehicleOpen(int vt, int t) { return vehicleOpen[vt][t]; }
    }

    private record MovementKey(int arcIndex, int slot) {}

    public record ModelStatistics(
            int variableCount,
            int constraintCount,
            long nonZeroCount,
            int horizonSlots,
            double periodDurationHours
    ) {}

    private record Model(double[] objective, boolean[] integer, double[] upperBounds,
                         List<SparsePublicMilpEngine.Constraint> constraints, long nonZeroCount) {}

    private record Reconstructed(
            double totalCost,
            double transportCost,
            double terminalCost,
            double rentalCost,
            double activationCost,
            double totalTonnage,
            double completionHour,
            int completionSlot,
            List<GlobalShipmentRoute> routes,
            List<ShipmentLegSchedule> legSchedules,
            List<ScheduledVehicleTrip> vehicleTrips,
            List<TerminalOperation> terminalOperations,
            Map<String, Double> arcTonnage
    ) {}

    private static int maxTrips(List<Arc> arcs) {
        int max = 1;
        for (Arc arc : arcs) max = Math.max(max, arc.maxTrips);
        return max;
    }

    private static final class TimeGrid {
        final double periodHours;
        final int horizonSlots;
        final int deadlineSlot;

        private TimeGrid(double periodHours, int horizonSlots, int deadlineSlot) {
            this.periodHours = periodHours;
            this.horizonSlots = horizonSlots;
            this.deadlineSlot = deadlineSlot;
        }

        static TimeGrid from(NewPostProblem problem) {
            double period = problem.getSchedulePeriodDurationHours();
            int horizon = (int) Math.ceil(problem.getPlanningHorizonHours() / period - EPS);
            if (horizon <= 0) throw new IllegalArgumentException("Planning horizon contains no time slots");

            double deadlineHours = problem.getPlanningHorizonHours();
            if (problem.getDeliveryDeadline() != null) {
                deadlineHours = Math.min(deadlineHours,
                        hoursFromReady(problem.getReadyTime(), problem.getDeliveryDeadline()));
            }
            int deadlineSlot = (int) Math.floor(deadlineHours / period + EPS);
            deadlineSlot = Math.max(0, Math.min(horizon, deadlineSlot));
            return new TimeGrid(period, horizon, deadlineSlot);
        }

        boolean finalProcessingAllowed(int slot) {
            return slot + 1 <= deadlineSlot;
        }
    }

    private static int travelSlots(double distance, double speedKmPerHour, double periodHours) {
        return Math.max(1, (int) Math.ceil(distance / speedKmPerHour / periodHours - EPS));
    }

    private static double hoursFromReady(String readyText, String deadlineText) {
        try {
            LocalTime ready = LocalTime.parse(readyText, TIME);
            LocalTime deadline = LocalTime.parse(deadlineText, TIME);
            long seconds = Duration.between(ready, deadline).getSeconds();
            if (seconds < 0) seconds += 24L * 3600L;
            return seconds / 3600.0;
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Times must use HH:mm format", ex);
        }
    }
}
