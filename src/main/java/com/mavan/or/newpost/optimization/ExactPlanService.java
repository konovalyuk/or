package com.mavan.or.newpost.optimization;

import com.mavan.or.newpost.global.GlobalExactNetworkOptimizer;
import com.mavan.or.newpost.global.GlobalExactNetworkResult;
import com.mavan.or.newpost.global.GlobalExactSettings;
import com.mavan.or.newpost.global.GlobalShipmentRoute;
import com.mavan.or.newpost.model.NewPostProblem;
import com.mavan.or.newpost.model.NewPostResult;
import com.mavan.or.newpost.model.ShipmentFlow;
import com.mavan.or.newpost.model.VehicleTrip;
import com.mavan.or.newpost.model.VehicleType;
import com.mavan.or.newpost.scheduling.*;

import java.util.*;

/**
 * Exact route, truck, terminal and schedule planning.
 *
 * 1. The static MILP (routes, split flows, consolidation, integer trips) is a
 *    relaxation of the full problem: it has the same costs but no time,
 *    terminal-capacity or fleet constraints. Its proven optimum is a lower
 *    bound for the full problem.
 * 2. The continuous-time scheduler tries to schedule exactly those trips and
 *    loads. If it succeeds, the full problem has a feasible plan whose cost
 *    equals the lower bound, so that plan is globally optimal.
 * 3. Otherwise the corrected time-expanded MILP is solved and its result is
 *    reported together with the static lower bound and the remaining gap.
 */
public final class ExactPlanService {
    private static final double EPS = 1e-6;

    private final NetworkOptimizer staticOptimizer = new NetworkOptimizer();
    private final ContinuousPlanScheduler scheduler = new ContinuousPlanScheduler();
    private final GlobalExactNetworkOptimizer globalOptimizer = new GlobalExactNetworkOptimizer();

    public record Options(boolean consolidation, long staticNodeLimit, GlobalExactSettings globalSettings, String currency) {
        public static Options defaults() {
            return new Options(true, 2_000_000L, new GlobalExactSettings(500L, 100_000, 50_000, 10_000_000L), "AED");
        }
    }

    /** Main plan plus the direct-only and fleet comparison scenarios. */
    public record Scenarios(ExactPlanResult plan, Map<String, ExactPlanResult> scenarios,
                            Double savingsVsDirect, Double savingsPercent) {}

    public Scenarios solveWithScenarios(NewPostProblem problem, Options options) {
        ExactPlanResult plan = solve(problem, options);
        Map<String, ExactPlanResult> scenarios = new LinkedHashMap<>();
        ExactPlanResult direct = problem.isDirectOnly() ? plan : solve(problem.toBuilder().directOnly(true).build(), options);
        scenarios.put("directOnly", direct);
        scenarios.put("optimizedNetwork", problem.isDirectOnly()
                ? solve(problem.toBuilder().directOnly(false).build(), options) : plan);
        if (problem.getOwnedVehicleLimits() != null) {
            scenarios.put("ownedFleetOnly", problem.isRentalEnabled()
                    ? solve(problem.toBuilder().rentalEnabled(false).rentalLimits(null).build(), options) : plan);
            if (problem.isRentalEnabled()) scenarios.put("ownedFleetWithRental", plan);
        }
        ExactPlanResult network = scenarios.get("optimizedNetwork");
        Double savings = null;
        Double percent = null;
        if (direct.hasPlan() && network.hasPlan()) {
            savings = direct.totalCost() - network.totalCost();
            percent = direct.totalCost() <= EPS ? 0.0 : savings / direct.totalCost() * 100.0;
        }
        return new Scenarios(plan, scenarios, savings, percent);
    }

    public ExactPlanResult solve(NewPostProblem problem, Options options) {
        Objects.requireNonNull(problem, "problem");
        if (!problem.getDiscountBands().isEmpty()) {
            throw new IllegalArgumentException("Volume discount bands are not supported by the exact planner.");
        }
        NewPostProblem relaxed = problem.toBuilder()
                .ownedVehicleLimits(null)
                .rentalEnabled(false)
                .rentalLimits(null)
                .deliveryDeadline(null)
                .routeActivationCost(null)
                .vehicleActivationCost(null)
                .build();

        StaticPlan staticPlan = options.consolidation()
                ? consolidatedStaticPlan(relaxed, options.staticNodeLimit())
                : perCommodityStaticPlan(relaxed, options.staticNodeLimit());
        if (staticPlan == null) {
            return failure(problem, ExactPlanResult.Status.INFEASIBLE,
                    "The routing model has no feasible plan.", List.of(), 0L, options);
        }
        Double lowerBound = staticPlan.proven ? staticPlan.totalCost : null;

        List<String> scheduleViolations = List.of();
        if (!hasActivationCosts(problem)) {
            ContinuousSchedule schedule = scheduler.schedule(problem, staticPlan.shipments, staticPlan.trips);
            if (schedule.feasible()) {
                return fromSchedule(problem, staticPlan, schedule, options);
            }
            scheduleViolations = schedule.violations();
        }

        if (!options.consolidation()) {
            List<String> warnings = new ArrayList<>(scheduleViolations);
            return failure(problem, ExactPlanResult.Status.NO_SOLUTION_FOUND,
                    "The cost-optimal plan without consolidation cannot be scheduled, and the time-expanded model always allows consolidation.",
                    warnings, staticPlan.nodes, options);
        }
        return fromGlobal(problem, staticPlan, lowerBound, scheduleViolations, options);
    }

    private static boolean hasActivationCosts(NewPostProblem problem) {
        double[][] route = problem.getRouteActivationCost();
        if (route != null) for (double[] row : route) for (double v : row) if (v > EPS) return true;
        double[] vehicle = problem.getVehicleActivationCost();
        if (vehicle != null) for (double v : vehicle) if (v > EPS) return true;
        return false;
    }

    private record StaticPlan(boolean proven, double totalCost, double transportCost, double terminalCost,
                              List<ShipmentFlow> shipments, List<ContinuousPlanScheduler.TripSpec> trips, long nodes) {}

    private StaticPlan consolidatedStaticPlan(NewPostProblem relaxed, long nodeLimit) {
        NewPostResult result = staticOptimizer.solve(relaxed, nodeLimit);
        if (result.getStatus() == NewPostResult.Status.INFEASIBLE) return null;
        List<ContinuousPlanScheduler.TripSpec> trips = new ArrayList<>();
        for (VehicleTrip trip : result.getVehicleTrips()) {
            trips.add(new ContinuousPlanScheduler.TripSpec(trip.getFrom(), trip.getTo(), trip.getVehicleTypeIndex(), null));
        }
        return new StaticPlan(result.getStatus() == NewPostResult.Status.OPTIMAL, result.getTotalCost(),
                result.getTransportCost(), result.getTerminalCost(), result.getShipments(), trips, result.getSearchNodes());
    }

    /** Without consolidation every OD flow has its own trucks, so the flows are independent problems. */
    private StaticPlan perCommodityStaticPlan(NewPostProblem relaxed, long nodeLimit) {
        double[][] demand = relaxed.getDemand();
        int n = relaxed.locationCount();
        boolean proven = true;
        double total = 0.0;
        double transport = 0.0;
        double terminal = 0.0;
        long nodes = 0L;
        List<ShipmentFlow> shipments = new ArrayList<>();
        List<ContinuousPlanScheduler.TripSpec> trips = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == j || demand[i][j] <= EPS) continue;
                double[][] single = new double[n][n];
                single[i][j] = demand[i][j];
                NewPostResult result = staticOptimizer.solve(relaxed.toBuilder().demand(single).build(), nodeLimit);
                if (result.getStatus() == NewPostResult.Status.INFEASIBLE) return null;
                proven &= result.getStatus() == NewPostResult.Status.OPTIMAL;
                total += result.getTotalCost();
                transport += result.getTransportCost();
                terminal += result.getTerminalCost();
                nodes += result.getSearchNodes();
                shipments.addAll(result.getShipments());
                for (VehicleTrip trip : result.getVehicleTrips()) {
                    trips.add(new ContinuousPlanScheduler.TripSpec(trip.getFrom(), trip.getTo(), trip.getVehicleTypeIndex(), i * n + j));
                }
            }
        }
        return new StaticPlan(proven, total, transport, terminal, shipments, trips, nodes);
    }

    private ExactPlanResult fromSchedule(NewPostProblem problem, StaticPlan plan, ContinuousSchedule schedule, Options options) {
        double transport = schedule.trips().stream().mapToDouble(PlannedTrip::transportCost).sum();
        double terminal = schedule.terminalJobs().stream().mapToDouble(PlannedTerminalJob::cost).sum();
        double total = transport + terminal;
        if (Math.abs(total - plan.totalCost) > EPS || Math.abs(transport - plan.transportCost) > EPS
                || Math.abs(terminal - plan.terminalCost) > EPS) {
            throw new IllegalStateException("Scheduled plan cost differs from the static plan cost");
        }
        List<RoutePlan> routes = routesFromSchedule(problem, plan.shipments, schedule);
        List<TerminalLoad> loads = terminalLoads(problem, schedule.terminalJobs());
        verifyTerminalCost(loads, terminal);

        boolean optimal = plan.proven;
        ExactPlanResult.Status status = optimal ? ExactPlanResult.Status.OPTIMAL : ExactPlanResult.Status.FEASIBLE_NOT_PROVEN;
        String message = optimal
                ? "Global optimum proven: the scheduled plan costs exactly the lower bound of the static relaxation."
                : "A scheduled plan was found, but the static relaxation was not solved to optimality within the node limit.";
        List<String> proof = new ArrayList<>();
        String c = options.currency();
        proof.add("Relaxation: the static MILP has the same transport and handling costs as the full problem but no time, terminal-capacity, fleet or deadline constraints, so its optimum is a lower bound.");
        proof.add(optimal
                ? "Branch-and-bound closed every branch of the static MILP after " + plan.nodes + " nodes: lower bound = " + money(plan.totalCost, c) + "."
                : "Branch-and-bound stopped at the node limit after " + plan.nodes + " nodes; no lower bound is proven.");
        proof.add("A continuous-time schedule exists for exactly the same routes, loads and " + schedule.trips().size()
                + " trips: every terminal processes at its capacity rate one job at a time, every truck leaves after all its loads were processed, completion "
                + hours(schedule.completionHour()) + " after ready time is within the " + hours(problem.getPlanningHorizonHours()) + " horizon"
                + (problem.getDeliveryDeadline() == null ? "" : " and the " + problem.getDeliveryDeadline() + " deadline")
                + (problem.getOwnedVehicleLimits() == null ? "" : " with the owned fleet limits") + ".");
        proof.add(optimal
                ? "Plan cost = " + money(total, c) + " = lower bound, gap 0%: the plan is globally optimal for the full problem."
                : "Plan cost = " + money(total, c) + "; optimality is not proven.");

        ExactPlanResult partial = new ExactPlanResult(status, message, "Static exact MILP + continuous-time schedule", optimal,
                total, optimal ? total : null, optimal ? 0.0 : null, transport, terminal, 0.0, 0.0,
                totalTonnage(problem), costPerTon(problem, total), truckKm(schedule.trips()), schedule.trips().size(),
                transshipmentTonnage(routes), schedule.completionHour(), routes, schedule.trips(), schedule.terminalJobs(),
                loads, proof, List.of(), List.of(), plan.nodes);
        return withExplanation(problem, partial, options);
    }

    private ExactPlanResult fromGlobal(NewPostProblem problem, StaticPlan plan, Double lowerBound,
                                       List<String> scheduleViolations, Options options) {
        GlobalExactNetworkResult global = globalOptimizer.solve(problem, options.globalSettings());
        List<String> warnings = new ArrayList<>();
        for (String violation : scheduleViolations) warnings.add("Cost-optimal static plan cannot be scheduled: " + violation);
        warnings.add("Times come from the time-expanded model with " + hours(problem.getSchedulePeriodDurationHours()) + " slots.");

        if (global.getStatus() == GlobalExactNetworkResult.Status.INFEASIBLE) {
            return failure(problem, ExactPlanResult.Status.INFEASIBLE, global.getMessage(), warnings, plan.nodes + global.getSearchNodes(), options);
        }
        if (global.getStatus() == GlobalExactNetworkResult.Status.MODEL_TOO_LARGE) {
            return failure(problem, ExactPlanResult.Status.MODEL_TOO_LARGE, global.getMessage(), warnings, plan.nodes, options);
        }
        if (global.getStatus() == GlobalExactNetworkResult.Status.SEARCH_LIMIT_REACHED_WITHOUT_FEASIBLE) {
            return failure(problem, ExactPlanResult.Status.NO_SOLUTION_FOUND, global.getMessage(), warnings, plan.nodes + global.getSearchNodes(), options);
        }

        List<Commodity> commodities = commodities(problem);
        List<PlannedTerminalJob> jobs = jobsFromGlobal(problem, commodities, global);
        List<PlannedTrip> trips = new ArrayList<>();
        int index = 0;
        for (ScheduledVehicleTrip trip : global.getVehicleTrips()) {
            VehicleType vehicle = problem.getVehicleTypes().get(trip.getVehicleTypeIndex());
            trips.add(new PlannedTrip(index++, trip.getFrom(), trip.getTo(), trip.getVehicleTypeIndex(), vehicle.getName(),
                    trip.isRented(), trip.getNominalCapacity(), trip.getUsableCapacity(), trip.getLoadedTonnage(),
                    trip.getDistance(), trip.getDepartureHour(), trip.getArrivalHour(), trip.getTransportCost(),
                    trip.getRentalCost(), List.of()));
        }
        List<RoutePlan> routes = new ArrayList<>();
        double[] handling = problem.getTerminalHandlingCostPerTon();
        for (GlobalShipmentRoute route : global.getRoutes()) {
            double perTon = 0.0;
            for (int city : route.getPath()) perTon += handling[city];
            routes.add(new RoutePlan(route.getCommodityIndex(), route.getOrigin(), route.getDestination(), route.getTonnage(),
                    route.getPath(), route.getTotalDistance(), route.getDepartureHour(), route.getArrivalHour()
                    + problem.getSchedulePeriodDurationHours(), route.getTonnage() * perTon));
        }
        List<TerminalLoad> loads = terminalLoads(problem, jobs);
        verifyTerminalCost(loads, global.getTerminalCost());

        double total = global.getTotalCost();
        boolean optimal = global.isGlobalOptimal() || (lowerBound != null && total <= lowerBound + EPS);
        Double bound = optimal ? Double.valueOf(total) : lowerBound;
        Double gap = bound == null ? null : Math.max(0.0, (total - bound) / Math.max(EPS, total) * 100.0);

        String c = options.currency();
        List<String> proof = new ArrayList<>();
        proof.add(lowerBound == null
                ? "The static relaxation was not solved to optimality; no static lower bound is available."
                : "Static relaxation lower bound = " + money(lowerBound, c) + ".");
        proof.add("The cost-optimal static plan could not be scheduled, so routes, trips and time slots were optimized jointly in the time-expanded MILP.");
        proof.add(global.isGlobalOptimal()
                ? "Branch-and-bound closed every branch of the time-expanded MILP after " + global.getSearchNodes() + " nodes: plan cost " + money(total, c) + " is optimal for the configured time grid."
                : "Branch-and-bound stopped at the node limit after " + global.getSearchNodes() + " nodes with plan cost " + money(total, c)
                + (gap == null ? "." : "; remaining gap to the lower bound " + String.format(Locale.ROOT, "%.2f", gap) + "%."));

        ExactPlanResult.Status status = optimal ? ExactPlanResult.Status.OPTIMAL : ExactPlanResult.Status.FEASIBLE_NOT_PROVEN;
        ExactPlanResult partial = new ExactPlanResult(status, global.getMessage(), "Time-expanded MILP (joint routes + schedule)", optimal,
                total, bound, gap, global.getTransportCost(), global.getTerminalCost(), global.getRentalCost(), global.getActivationCost(),
                totalTonnage(problem), costPerTon(problem, total), truckKm(trips), trips.size(), transshipmentTonnage(routes),
                global.getCompletionHour(), routes, trips, jobs, loads, proof, List.of(), warnings,
                plan.nodes + global.getSearchNodes());
        return withExplanation(problem, partial, options);
    }

    private static List<PlannedTerminalJob> jobsFromGlobal(NewPostProblem problem, List<Commodity> commodities,
                                                           GlobalExactNetworkResult global) {
        double[] capacity = problem.getTerminalCapacityPerHour();
        Map<String, Double> slotUsage = new HashMap<>();
        List<TerminalOperation> operations = new ArrayList<>(global.getTerminalOperations());
        operations.sort(Comparator.comparingInt(TerminalOperation::getStartSlot).thenComparingInt(TerminalOperation::getCity));
        List<PlannedTerminalJob> jobs = new ArrayList<>();
        for (TerminalOperation op : operations) {
            Commodity commodity = commodities.get(op.getShipmentIndex());
            PlannedTerminalJob.Kind kind = op.getCity() == commodity.origin ? PlannedTerminalJob.Kind.ORIGIN
                    : op.getCity() == commodity.destination ? PlannedTerminalJob.Kind.DESTINATION
                    : PlannedTerminalJob.Kind.TRANSIT;
            String key = op.getCity() + ":" + op.getStartSlot();
            double used = slotUsage.getOrDefault(key, 0.0);
            double start = op.getStartHour() + used / capacity[op.getCity()];
            double end = start + op.getTonnage() / capacity[op.getCity()];
            slotUsage.put(key, used + op.getTonnage());
            jobs.add(new PlannedTerminalJob(op.getCity(), op.getShipmentIndex(), op.getVisitIndex(), kind,
                    op.getTonnage(), start, end, op.getCost()));
        }
        return jobs;
    }

    private ExactPlanResult failure(NewPostProblem problem, ExactPlanResult.Status status, String message,
                                    List<String> warnings, long nodes, Options options) {
        ExactPlanResult partial = new ExactPlanResult(status, message, "none", false, null, null, null,
                0.0, 0.0, 0.0, 0.0, totalTonnage(problem), null, 0.0, 0, 0.0, null,
                List.of(), List.of(), List.of(), List.of(), List.of(message), List.of(), warnings, nodes);
        return withExplanation(problem, partial, options);
    }

    private static ExactPlanResult withExplanation(NewPostProblem problem, ExactPlanResult r, Options options) {
        List<ExplanationSection> explanation = PlanExplanation.build(problem, r, options.currency(), options.consolidation());
        return new ExactPlanResult(r.status(), r.message(), r.method(), r.globallyOptimal(), r.totalCost(), r.lowerBound(),
                r.gapPercent(), r.transportCost(), r.terminalCost(), r.rentalCost(), r.activationCost(), r.totalTonnage(),
                r.costPerTon(), r.totalTruckKm(), r.tripCount(), r.transshipmentTonnage(), r.completionHour(), r.routes(),
                r.trips(), r.terminalJobs(), r.terminalLoads(), r.proof(), explanation, r.warnings(), r.searchNodes());
    }

    private static List<RoutePlan> routesFromSchedule(NewPostProblem problem, List<ShipmentFlow> shipments, ContinuousSchedule schedule) {
        double[][] distance = problem.getDistance();
        double[] handling = problem.getTerminalHandlingCostPerTon();
        List<RoutePlan> routes = new ArrayList<>();
        for (int s = 0; s < shipments.size(); s++) {
            ShipmentFlow flow = shipments.get(s);
            double km = 0.0;
            double perTon = 0.0;
            List<Integer> path = flow.getPath();
            for (int k = 0; k < path.size(); k++) {
                perTon += handling[path.get(k)];
                if (k + 1 < path.size()) km += distance[path.get(k)][path.get(k + 1)];
            }
            double departure = Double.POSITIVE_INFINITY;
            for (PlannedTrip trip : schedule.trips()) {
                for (TripLoad load : trip.loads()) {
                    if (load.shipmentIndex() == s && load.legIndex() == 0) departure = Math.min(departure, trip.departureHour());
                }
            }
            double delivered = 0.0;
            for (PlannedTerminalJob job : schedule.terminalJobs()) {
                if (job.shipmentIndex() == s && job.kind() == PlannedTerminalJob.Kind.DESTINATION) delivered = job.endHour();
            }
            routes.add(new RoutePlan(s, flow.getOrigin(), flow.getDestination(), flow.getTonnage(), path, km,
                    departure, delivered, flow.getTonnage() * perTon));
        }
        routes.sort(Comparator.comparingInt(RoutePlan::origin).thenComparingInt(RoutePlan::destination)
                .thenComparingInt(r -> r.path().size()));
        return routes;
    }

    static List<TerminalLoad> terminalLoads(NewPostProblem problem, List<PlannedTerminalJob> jobs) {
        List<TerminalLoad> loads = new ArrayList<>();
        for (int city = 0; city < problem.locationCount(); city++) {
            loads.add(TerminalLoad.of(city, problem.getTerminalCapacityPerHour()[city],
                    problem.getTerminalHandlingCostPerTon()[city], problem.getPlanningHorizonHours(), jobs));
        }
        return loads;
    }

    private static void verifyTerminalCost(List<TerminalLoad> loads, double terminalCost) {
        double sum = loads.stream().mapToDouble(TerminalLoad::cost).sum();
        if (Math.abs(sum - terminalCost) > EPS) {
            throw new IllegalStateException("Terminal workload cost " + sum + " differs from terminal cost " + terminalCost);
        }
        for (TerminalLoad load : loads) {
            if (load.peakRatePerHour() > load.capacityPerHour() + EPS) {
                throw new IllegalStateException("Terminal " + load.city() + " exceeds its capacity");
            }
        }
    }

    private record Commodity(int origin, int destination) {}

    private static List<Commodity> commodities(NewPostProblem problem) {
        double[][] demand = problem.getDemand();
        List<Commodity> result = new ArrayList<>();
        for (int i = 0; i < demand.length; i++) {
            for (int j = 0; j < demand.length; j++) if (i != j && demand[i][j] > 1e-7) result.add(new Commodity(i, j));
        }
        return result;
    }

    static double totalTonnage(NewPostProblem problem) {
        double total = 0.0;
        for (double[] row : problem.getDemand()) for (double v : row) total += v;
        return total;
    }

    private static Double costPerTon(NewPostProblem problem, double total) {
        double tonnage = totalTonnage(problem);
        return tonnage <= EPS ? null : total / tonnage;
    }

    private static double truckKm(List<PlannedTrip> trips) {
        return trips.stream().mapToDouble(PlannedTrip::distance).sum();
    }

    private static double transshipmentTonnage(List<RoutePlan> routes) {
        return routes.stream().filter(RoutePlan::transshipment).mapToDouble(RoutePlan::tonnage).sum();
    }

    static String money(double value, String currency) {
        return String.format(Locale.ROOT, "%,.2f %s", value, currency);
    }

    static String hours(double value) {
        long minutes = Math.round(value * 60.0);
        return String.format(Locale.ROOT, "%d h %02d min", minutes / 60, minutes % 60);
    }
}
