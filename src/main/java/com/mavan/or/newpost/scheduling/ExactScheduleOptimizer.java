package com.mavan.or.newpost.scheduling;

import com.mavan.or.newpost.model.Commodity;
import com.mavan.or.newpost.model.DirectedArc;
import com.mavan.or.newpost.model.NewPostProblem;
import com.mavan.or.newpost.model.NewPostResult;
import com.mavan.or.newpost.model.ShipmentFlow;
import com.mavan.or.newpost.model.VehicleType;
import com.mavan.or.newpost.optimization.PublicMilpEngine;

import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Exact time-indexed scheduler for a fixed static New Post routing plan.
 *
 * The routing/commodity split is taken from {@link NewPostResult}. The
 * scheduler then optimizes only schedule-dependent decisions:
 *
 *  - departure times;
 *  - integer owned truck trips;
 *  - integer rental truck trips;
 *  - consolidation of shipment flows in the same physical trip;
 *  - terminal capacity by time slot;
 *  - finite simultaneous fleet availability;
 *  - hard delivery deadline.
 *
 * The mathematical model is a time-expanded MILP. It is exact for the
 * configured discrete time grid. Travel durations are rounded up to whole
 * slots and terminal processing happens during whole slots.
 */
public final class ExactScheduleOptimizer {
    private static final double EPS = 1e-7;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    public ScheduledNetworkResult solve(NewPostProblem problem, NewPostResult staticPlan) {
        return solve(problem, staticPlan, 1_000_000L);
    }

    public ScheduledNetworkResult solve(
            NewPostProblem problem,
            NewPostResult staticPlan,
            long nodeLimit
    ) {
        Objects.requireNonNull(problem, "problem");
        Objects.requireNonNull(staticPlan, "staticPlan");
        if (nodeLimit <= 0) throw new IllegalArgumentException("Node limit must be positive");

        if (staticPlan.getStatus() == NewPostResult.Status.INFEASIBLE
                || staticPlan.getShipments().isEmpty()) {
            return new ScheduledNetworkResult(
                    ScheduledNetworkResult.Status.STATIC_PLAN_NOT_AVAILABLE,
                    "Static routing plan is unavailable for scheduling.",
                    staticPlan.getStatus() == NewPostResult.Status.OPTIMAL,
                    false,
                    staticPlan.getTotalCost(),
                    Double.POSITIVE_INFINITY,
                    0.0,
                    staticPlan.getTerminalCost(),
                    0.0,
                    staticPlan.getTotalTonnage(),
                    Double.POSITIVE_INFINITY,
                    -1,
                    0,
                    problem.getSchedulePeriodDurationHours(),
                    List.of(), List.of(), List.of(), Map.of(), 0
            );
        }

        List<ShipmentFlow> shipments = staticPlan.getShipments();
        validateStaticPlan(problem, shipments);

        TimeGrid grid = TimeGrid.from(problem);
        List<ArcInfo> arcs = buildArcInfos(problem, shipments);
        Map<ArcKey, Integer> arcIndex = new LinkedHashMap<>();
        for (int i = 0; i < arcs.size(); i++) arcIndex.put(arcs.get(i).key, i);

        int vehicleCount = problem.getVehicleTypes().size();
        VariableIndex vars = VariableIndex.build(problem, shipments, arcs, grid, vehicleCount);
        ModelData model = buildModel(problem, staticPlan, shipments, arcs, arcIndex, grid, vars);

        PublicMilpEngine.SolveResult solved = PublicMilpEngine.solve(
                vars.variableCount,
                model.objective,
                model.integer,
                model.upperBounds,
                model.constraints,
                null,
                nodeLimit
        );

        if (!solved.feasible()) {
            return new ScheduledNetworkResult(
                    ScheduledNetworkResult.Status.INFEASIBLE,
                    buildInfeasibleMessage(problem, grid),
                    staticPlan.getStatus() == NewPostResult.Status.OPTIMAL,
                    false,
                    staticPlan.getTotalCost(),
                    Double.POSITIVE_INFINITY,
                    0.0,
                    staticPlan.getTerminalCost(),
                    0.0,
                    staticPlan.getTotalTonnage(),
                    Double.POSITIVE_INFINITY,
                    -1,
                    grid.horizonSlots,
                    grid.periodHours,
                    List.of(), List.of(), List.of(), arcTonnageMap(problem, shipments), solved.visitedNodes()
            );
        }

        Solution solution = reconstructSolution(problem, staticPlan, shipments, arcs, grid, vars, solved.solution());

        ScheduledNetworkResult.Status status = solved.provenOptimal()
                ? ScheduledNetworkResult.Status.OPTIMAL
                : ScheduledNetworkResult.Status.FEASIBLE_NOT_PROVEN;

        boolean staticOptimal = staticPlan.getStatus() == NewPostResult.Status.OPTIMAL;

        String message;
        if (solved.provenOptimal()) {
            message = "Exact schedule optimum proven for the supplied static routing plan. "
                    + "Routing itself is not re-optimized by this scheduling layer.";
        } else {
            message = "A feasible exact-model schedule was found, but schedule optimality was not proven "
                    + "before the MILP node limit. The static routing plan remains unchanged.";
        }

        boolean globallyOptimal = false;
        // The scheduling layer cannot prove global routing+schedule optimality because the
        // static routing solution is intentionally kept unchanged. Even a static optimum can
        // become infeasible under finite fleet/deadline/rental constraints while another routing
        // plan could still be feasible.

        return new ScheduledNetworkResult(
                status,
                message,
                staticOptimal,
                globallyOptimal,
                staticPlan.getTotalCost(),
                solution.totalCost,
                solution.transportCost,
                staticPlan.getTerminalCost(),
                solution.rentalCost,
                staticPlan.getTotalTonnage(),
                solution.completionHour,
                solution.completionSlot,
                grid.horizonSlots,
                grid.periodHours,
                solution.vehicleTrips,
                solution.terminalOperations,
                solution.legSchedules,
                arcTonnageMap(problem, shipments),
                solved.visitedNodes()
        );
    }

    private static ModelData buildModel(
            NewPostProblem problem,
            NewPostResult staticPlan,
            List<ShipmentFlow> shipments,
            List<ArcInfo> arcs,
            Map<ArcKey, Integer> arcIndex,
            TimeGrid grid,
            VariableIndex vars
    ) {
        List<PublicMilpEngine.Constraint> constraints = new ArrayList<>();
        double[] objective = new double[vars.variableCount];
        boolean[] integer = new boolean[vars.variableCount];
        double[] upperBounds = new double[vars.variableCount];
        java.util.Arrays.fill(upperBounds, Double.POSITIVE_INFINITY);

        List<VehicleType> vehicles = problem.getVehicleTypes();
        double loadingFactor = problem.getLoadingFactor();

        // Shipment visit processing and leg departure bounds.
        for (int s = 0; s < shipments.size(); s++) {
            ShipmentFlow shipment = shipments.get(s);
            double amount = shipment.getTonnage();

            for (int v = 0; v < shipment.getPath().size(); v++) {
                for (int t = 0; t < grid.horizonSlots; t++) {
                    int idx = vars.process(s, v, t);
                    upperBounds[idx] = amount;
                    if (v == shipment.getPath().size() - 1
                            && !grid.finalProcessingAllowed(t)) {
                        upperBounds[idx] = 0.0;
                    }
                }

                // Exactly one full processing of this visit.
                double[] eq = new double[vars.variableCount];
                for (int t = 0; t < grid.horizonSlots; t++) eq[vars.process(s, v, t)] = 1.0;
                addEquality(constraints, eq, amount);

                // Cannot process more than has arrived by the beginning of slot t.
                for (int t = 0; t < grid.horizonSlots; t++) {
                    double[] a = new double[vars.variableCount];
                    for (int tau = 0; tau <= t; tau++) a[vars.process(s, v, tau)] = 1.0;

                    if (v == 0) {
                        constraints.add(new PublicMilpEngine.Constraint(a, amount));
                    } else {
                        int prevLeg = v - 1;
                        int travelSlots = vars.travelSlots(s, prevLeg);
                        int latestDeparture = t - travelSlots;
                        if (latestDeparture < 0) {
                            constraints.add(new PublicMilpEngine.Constraint(a, 0.0));
                        } else {
                            for (int tau = 0; tau <= latestDeparture && tau < grid.horizonSlots; tau++) {
                                a[vars.depart(s, prevLeg, tau)] -= 1.0;
                            }
                            constraints.add(new PublicMilpEngine.Constraint(a, 0.0));
                        }
                    }
                }

                // All processed flow for this visit can depart on the next leg only
                // from the following slot, i.e. processing consumes one full slot.
                if (v < shipment.getPath().size() - 1) {
                    for (int t = 0; t < grid.horizonSlots; t++) {
                        double[] a = new double[vars.variableCount];
                        for (int tau = 0; tau <= t; tau++) a[vars.depart(s, v, tau)] = 1.0;
                        for (int tau = 0; tau < t; tau++) a[vars.process(s, v, tau)] -= 1.0;
                        constraints.add(new PublicMilpEngine.Constraint(a, 0.0));
                    }

                    double[] departureEq = new double[vars.variableCount];
                    for (int t = 0; t < grid.horizonSlots; t++) departureEq[vars.depart(s, v, t)] = 1.0;
                    addEquality(constraints, departureEq, amount);

                    for (int t = 0; t < grid.horizonSlots; t++) {
                        int idx = vars.depart(s, v, t);
                        upperBounds[idx] = amount;
                        if (!vars.canDepartWithinHorizon(s, v, t)) upperBounds[idx] = 0.0;
                    }
                }
            }
        }

        // Shared terminal capacity per time slot.
        double[] terminalCapacity = problem.getTerminalCapacityPerHour();
        for (int city = 0; city < problem.locationCount(); city++) {
            for (int t = 0; t < grid.horizonSlots; t++) {
                double[] a = new double[vars.variableCount];
                for (int s = 0; s < shipments.size(); s++) {
                    List<Integer> path = shipments.get(s).getPath();
                    for (int v = 0; v < path.size(); v++) {
                        if (path.get(v) == city) a[vars.process(s, v, t)] += 1.0;
                    }
                }
                double rhs = terminalCapacity[city] * grid.periodHours;
                constraints.add(new PublicMilpEngine.Constraint(a, rhs));
            }
        }

        // Shared physical vehicle capacity per arc and departure slot.
        for (int ai = 0; ai < arcs.size(); ai++) {
            ArcInfo arc = arcs.get(ai);
            for (int t = 0; t < grid.horizonSlots; t++) {
                double[] a = new double[vars.variableCount];
                for (int s = 0; s < shipments.size(); s++) {
                    List<Integer> path = shipments.get(s).getPath();
                    for (int leg = 0; leg < path.size() - 1; leg++) {
                        if (arc.key.from == path.get(leg) && arc.key.to == path.get(leg + 1)) {
                            a[vars.depart(s, leg, t)] += 1.0;
                        }
                    }
                }
                for (int vt = 0; vt < vehicles.size(); vt++) {
                    VehicleType vehicle = vehicles.get(vt);
                    double capacity = vehicle.usableCapacity(loadingFactor);
                    a[vars.ownedTrip(ai, vt, t)] -= capacity;
                    a[vars.rentalTrip(ai, vt, t)] -= capacity;

                    int owned = vars.ownedTrip(ai, vt, t);
                    int rental = vars.rentalTrip(ai, vt, t);
                    integer[owned] = true;
                    integer[rental] = true;

                    double perTripTransport = arc.distance * vehicle.getTariffPerKm();
                    objective[owned] += perTripTransport;
                    objective[rental] += perTripTransport + vehicle.getRentalCostPerTrip();

                    int maxTrips = vars.maxTripsForArc(arc);
                    upperBounds[owned] = maxTrips;
                    upperBounds[rental] = problem.isRentalEnabled() ? maxTrips : 0.0;

                    if (!vars.canTripFinishWithinHorizon(ai, vt, t)) {
                        upperBounds[owned] = 0.0;
                        upperBounds[rental] = 0.0;
                    }
                }
                constraints.add(new PublicMilpEngine.Constraint(a, 0.0));
            }
        }

        // Simultaneous owned fleet availability.
        int[] ownedLimits = problem.getOwnedVehicleLimits();
        if (ownedLimits != null) {
            for (int vt = 0; vt < vehicles.size(); vt++) {
                for (int t = 0; t < grid.horizonSlots; t++) {
                    double[] a = new double[vars.variableCount];
                    for (int ai = 0; ai < arcs.size(); ai++) {
                        int duration = vars.travelSlotsForArc(ai);
                        for (int start = 0; start < grid.horizonSlots; start++) {
                            if (start <= t && t < start + duration) {
                                a[vars.ownedTrip(ai, vt, start)] += 1.0;
                            }
                        }
                    }
                    constraints.add(new PublicMilpEngine.Constraint(a, ownedLimits[vt]));
                }
            }
        }

        // Rental fleet availability. A null rental limit means unlimited rental
        // vehicles when rental is enabled; disabled rental is already upper-bounded at 0.
        int[] rentalLimits = problem.getRentalLimits();
        if (problem.isRentalEnabled() && rentalLimits != null) {
            for (int vt = 0; vt < vehicles.size(); vt++) {
                for (int t = 0; t < grid.horizonSlots; t++) {
                    double[] a = new double[vars.variableCount];
                    for (int ai = 0; ai < arcs.size(); ai++) {
                        int duration = vars.travelSlotsForArc(ai);
                        for (int start = 0; start < grid.horizonSlots; start++) {
                            if (start <= t && t < start + duration) {
                                a[vars.rentalTrip(ai, vt, start)] += 1.0;
                            }
                        }
                    }
                    constraints.add(new PublicMilpEngine.Constraint(a, rentalLimits[vt]));
                }
            }
        }

        // Tighten rental-disabled variables even if no fleet constraint exists.
        if (!problem.isRentalEnabled()) {
            for (int ai = 0; ai < arcs.size(); ai++) {
                for (int vt = 0; vt < vehicles.size(); vt++) {
                    for (int t = 0; t < grid.horizonSlots; t++) {
                        upperBounds[vars.rentalTrip(ai, vt, t)] = 0.0;
                    }
                }
            }
        }

        // If the user's deadline is earlier than the planning horizon, prohibit
        // departures that cannot arrive and final processing that cannot complete.
        return new ModelData(objective, integer, upperBounds, constraints);
    }

    private static Solution reconstructSolution(
            NewPostProblem problem,
            NewPostResult staticPlan,
            List<ShipmentFlow> shipments,
            List<ArcInfo> arcs,
            TimeGrid grid,
            VariableIndex vars,
            double[] x
    ) {
        List<ScheduledVehicleTrip> trips = new ArrayList<>();
        List<ShipmentLegSchedule> legSchedules = new ArrayList<>();
        List<TerminalOperation> terminalOperations = new ArrayList<>();

        double transportCost = 0.0;
        double rentalCost = 0.0;
        double completionHour = 0.0;
        int completionSlot = 0;

        for (int s = 0; s < shipments.size(); s++) {
            ShipmentFlow shipment = shipments.get(s);
            List<Integer> path = shipment.getPath();

            for (int v = 0; v < path.size(); v++) {
                int city = path.get(v);
                List<OperationChunk> chunks = new ArrayList<>();
                for (int t = 0; t < grid.horizonSlots; t++) {
                    double q = clean(x[vars.process(s, v, t)]);
                    if (q <= EPS) continue;
                    chunks.add(new OperationChunk(t, q));
                }
                terminalOperations.addAll(mergeTerminalChunks(
                        problem, s, v, city, chunks, grid
                ));

                if (v == path.size() - 1) {
                    for (OperationChunk chunk : chunks) {
                        int end = chunk.slot + 1;
                        completionSlot = Math.max(completionSlot, end);
                        completionHour = Math.max(completionHour, end * grid.periodHours);
                    }
                }
            }

            for (int leg = 0; leg < path.size() - 1; leg++) {
                int from = path.get(leg);
                int to = path.get(leg + 1);
                int travelSlots = vars.travelSlots(s, leg);
                for (int t = 0; t < grid.horizonSlots; t++) {
                    double q = clean(x[vars.depart(s, leg, t)]);
                    if (q <= EPS) continue;
                    int arrivalSlot = t + travelSlots;
                    legSchedules.add(new ShipmentLegSchedule(
                            s,
                            leg,
                            from,
                            to,
                            q,
                            t,
                            arrivalSlot,
                            t * grid.periodHours,
                            arrivalSlot * grid.periodHours
                    ));
                }
            }
        }

        // Allocate the aggregated load on each physical departure slot among
        // the selected truck trips. Any split among trucks with the same
        // departure is equivalent because the capacity and cost are already
        // fixed by the MILP decision.
        for (int ai = 0; ai < arcs.size(); ai++) {
            ArcInfo arc = arcs.get(ai);
            for (int t = 0; t < grid.horizonSlots; t++) {
                double totalLoad = 0.0;
                for (ShipmentLegSchedule leg : legSchedules) {
                    if (leg.getDepartureSlot() == t
                            && leg.getFrom() == arc.key.from
                            && leg.getTo() == arc.key.to) {
                        totalLoad += leg.getTonnage();
                    }
                }

                if (totalLoad <= EPS) continue;

                for (int vt = 0; vt < problem.getVehicleTypes().size(); vt++) {
                    VehicleType vehicle = problem.getVehicleTypes().get(vt);
                    int ownedCount = (int) Math.round(clean(x[vars.ownedTrip(ai, vt, t)]));
                    int rentalCount = (int) Math.round(clean(x[vars.rentalTrip(ai, vt, t)]));
                    int duration = vars.travelSlotsForArc(ai);
                    double usable = vehicle.usableCapacity(problem.getLoadingFactor());
                    double tripTransport = arc.distance * vehicle.getTariffPerKm();
                    double remaining = totalLoad;

                    for (int count = 0; count < ownedCount; count++) {
                        double load = Math.min(usable, Math.max(0.0, remaining));
                        trips.add(new ScheduledVehicleTrip(
                                arc.key.from,
                                arc.key.to,
                                vt,
                                false,
                                load,
                                vehicle.getNominalCapacity(),
                                usable,
                                arc.distance,
                                t,
                                t + duration,
                                t * grid.periodHours,
                                (t + duration) * grid.periodHours,
                                tripTransport,
                                0.0
                        ));
                        remaining -= load;
                        transportCost += tripTransport;
                    }
                    for (int count = 0; count < rentalCount; count++) {
                        double load = Math.min(usable, Math.max(0.0, remaining));
                        double rental = vehicle.getRentalCostPerTrip();
                        trips.add(new ScheduledVehicleTrip(
                                arc.key.from,
                                arc.key.to,
                                vt,
                                true,
                                load,
                                vehicle.getNominalCapacity(),
                                usable,
                                arc.distance,
                                t,
                                t + duration,
                                t * grid.periodHours,
                                (t + duration) * grid.periodHours,
                                tripTransport,
                                rental
                        ));
                        remaining -= load;
                        transportCost += tripTransport;
                        rentalCost += rental;
                    }
                    if (remaining < EPS) remaining = 0.0;
                    if (remaining > 1e-5) {
                        throw new IllegalStateException("Reconstructed trips do not cover scheduled load on "
                                + arc.key + " at slot " + t + ": remaining=" + remaining);
                    }
                }
            }
        }

        double totalCost = transportCost + rentalCost + staticPlan.getTerminalCost();
        verifySolution(problem, staticPlan, shipments, trips, terminalOperations, legSchedules,
                totalCost, transportCost, rentalCost, grid);

        return new Solution(
                totalCost,
                transportCost,
                rentalCost,
                completionHour,
                completionSlot,
                trips,
                terminalOperations,
                legSchedules
        );
    }

    private static List<TerminalOperation> mergeTerminalChunks(
            NewPostProblem problem,
            int shipmentIndex,
            int visitIndex,
            int city,
            List<OperationChunk> chunks,
            TimeGrid grid
    ) {
        if (chunks.isEmpty()) return List.of();
        List<TerminalOperation> result = new ArrayList<>();
        int start = chunks.get(0).slot;
        int end = start + 1;
        double amount = chunks.get(0).tonnage;

        for (int i = 1; i < chunks.size(); i++) {
            OperationChunk next = chunks.get(i);
            if (next.slot == end) {
                end++;
                amount += next.tonnage;
            } else {
                result.add(operation(problem, shipmentIndex, visitIndex, city, amount, start, end, grid));
                start = next.slot;
                end = start + 1;
                amount = next.tonnage;
            }
        }
        result.add(operation(problem, shipmentIndex, visitIndex, city, amount, start, end, grid));
        return result;
    }

    private static TerminalOperation operation(
            NewPostProblem problem,
            int shipmentIndex,
            int visitIndex,
            int city,
            double amount,
            int start,
            int end,
            TimeGrid grid
    ) {
        double cost = amount * problem.getTerminalHandlingCostPerTon()[city];
        return new TerminalOperation(
                shipmentIndex,
                city,
                visitIndex,
                amount,
                start,
                end,
                start * grid.periodHours,
                end * grid.periodHours,
                cost
        );
    }

    private static void verifySolution(
            NewPostProblem problem,
            NewPostResult staticPlan,
            List<ShipmentFlow> shipments,
            List<ScheduledVehicleTrip> trips,
            List<TerminalOperation> terminalOperations,
            List<ShipmentLegSchedule> legSchedules,
            double totalCost,
            double transportCost,
            double rentalCost,
            TimeGrid grid
    ) {
        // Every fixed static shipment flow must have exactly its tonnage on
        // every scheduled leg and exactly one full handling operation per visit.
        for (int s = 0; s < shipments.size(); s++) {
            double shipment = shipments.get(s).getTonnage();
            for (int leg = 0; leg < shipments.get(s).getPath().size() - 1; leg++) {
                double moved = 0.0;
                for (ShipmentLegSchedule schedule : legSchedules) {
                    if (schedule.getShipmentIndex() == s && schedule.getLegIndex() == leg) moved += schedule.getTonnage();
                }
                if (Math.abs(moved - shipment) > 1e-5) {
                    throw new IllegalStateException("Scheduled leg balance failed for shipment " + s + ": expected=" + shipment + ", actual=" + moved);
                }
            }
        }

        double calcTotal = staticPlan.getTerminalCost() + transportCost + rentalCost;
        if (Math.abs(calcTotal - totalCost) > 1e-5) throw new IllegalStateException("Scheduled cost decomposition failed");

        for (ScheduledVehicleTrip trip : trips) {
            if (trip.getLoadedTonnage() > trip.getUsableCapacity() + 1e-5) {
                throw new IllegalStateException("Trip capacity violated");
            }
            if (trip.getArrivalSlot() > grid.horizonSlots) {
                throw new IllegalStateException("Trip ends outside horizon");
            }
        }
    }

    private static void validateStaticPlan(NewPostProblem problem, List<ShipmentFlow> shipments) {
        double[][] distance = problem.getDistance();
        double[] handling = problem.getTerminalHandlingCostPerTon();
        for (ShipmentFlow shipment : shipments) {
            if (shipment.getPath().isEmpty()) throw new IllegalArgumentException("Static shipment contains empty path");
            if (shipment.getPath().get(0) != shipment.getOrigin()) throw new IllegalArgumentException("Static shipment path origin mismatch");
            if (shipment.getPath().get(shipment.getPath().size() - 1) != shipment.getDestination()) throw new IllegalArgumentException("Static shipment path destination mismatch");
            for (int k = 0; k < shipment.getPath().size() - 1; k++) {
                int from = shipment.getPath().get(k);
                int to = shipment.getPath().get(k + 1);
                if (from == to) throw new IllegalArgumentException("Self route in static shipment path");
                if (!(distance[from][to] > 0.0)) throw new IllegalArgumentException("Missing directed distance for static shipment path");
            }
            for (int city : shipment.getPath()) {
                if (city < 0 || city >= handling.length) throw new IllegalArgumentException("Invalid city in shipment path");
            }
        }
    }

    private static List<ArcInfo> buildArcInfos(NewPostProblem problem, List<ShipmentFlow> shipments) {
        Map<ArcKey, ArcInfo> map = new LinkedHashMap<>();
        double[][] distance = problem.getDistance();
        for (ShipmentFlow shipment : shipments) {
            for (int k = 0; k < shipment.getPath().size() - 1; k++) {
                ArcKey key = new ArcKey(shipment.getPath().get(k), shipment.getPath().get(k + 1));
                map.putIfAbsent(key, new ArcInfo(key, distance[key.from][key.to]));
            }
        }
        return new ArrayList<>(map.values());
    }

    private static Map<String, Double> arcTonnageMap(NewPostProblem problem, List<ShipmentFlow> shipments) {
        Map<String, Double> result = new LinkedHashMap<>();
        for (ShipmentFlow shipment : shipments) {
            List<Integer> path = shipment.getPath();
            for (int k = 0; k < path.size() - 1; k++) {
                String key = problem.getLocations().get(path.get(k)) + " -> " + problem.getLocations().get(path.get(k + 1));
                result.merge(key, shipment.getTonnage(), Double::sum);
            }
        }
        return result;
    }

    private static String buildInfeasibleMessage(NewPostProblem problem, TimeGrid grid) {
        StringBuilder s = new StringBuilder("No schedule satisfies the fixed routing plan with the supplied constraints.");
        if (problem.getOwnedVehicleLimits() != null) s.append(" Check finite owned-fleet availability.");
        if (problem.isRentalEnabled()) s.append(" Rental capacity is enabled and may also be limiting.");
        if (problem.getDeliveryDeadline() != null) s.append(" The hard delivery deadline is active.");
        s.append(" Effective horizon: ").append(grid.horizonSlots).append(" slots.");
        return s.toString();
    }

    private static double clean(double v) {
        return Math.abs(v) <= EPS ? 0.0 : v;
    }

    private static void addEquality(List<PublicMilpEngine.Constraint> constraints, double[] coefficients, double rhs) {
        constraints.add(new PublicMilpEngine.Constraint(coefficients.clone(), rhs));
        negate(coefficients);
        constraints.add(new PublicMilpEngine.Constraint(coefficients.clone(), -rhs));
    }

    private static void negate(double[] a) {
        for (int i = 0; i < a.length; i++) a[i] = -a[i];
    }

    private static final class ModelData {
        final double[] objective;
        final boolean[] integer;
        final double[] upperBounds;
        final List<PublicMilpEngine.Constraint> constraints;

        ModelData(double[] objective, boolean[] integer, double[] upperBounds,
                  List<PublicMilpEngine.Constraint> constraints) {
            this.objective = objective;
            this.integer = integer;
            this.upperBounds = upperBounds;
            this.constraints = constraints;
        }
    }

    private static final class Solution {
        final double totalCost;
        final double transportCost;
        final double rentalCost;
        final double completionHour;
        final int completionSlot;
        final List<ScheduledVehicleTrip> vehicleTrips;
        final List<TerminalOperation> terminalOperations;
        final List<ShipmentLegSchedule> legSchedules;

        Solution(double totalCost, double transportCost, double rentalCost,
                 double completionHour, int completionSlot,
                 List<ScheduledVehicleTrip> vehicleTrips,
                 List<TerminalOperation> terminalOperations,
                 List<ShipmentLegSchedule> legSchedules) {
            this.totalCost = totalCost;
            this.transportCost = transportCost;
            this.rentalCost = rentalCost;
            this.completionHour = completionHour;
            this.completionSlot = completionSlot;
            this.vehicleTrips = vehicleTrips;
            this.terminalOperations = terminalOperations;
            this.legSchedules = legSchedules;
        }
    }

    private record OperationChunk(int slot, double tonnage) {}

    private static final class ArcInfo {
        final ArcKey key;
        final double distance;
        ArcInfo(ArcKey key, double distance) { this.key = key; this.distance = distance; }
    }

    private record ArcKey(int from, int to) {
        @Override public String toString() { return from + "->" + to; }
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

        static TimeGrid from(NewPostProblem p) {
            double period = p.getSchedulePeriodDurationHours();
            int horizon = (int) Math.ceil(p.getPlanningHorizonHours() / period - EPS);
            if (horizon <= 0) throw new IllegalArgumentException("Planning horizon contains no time slots");

            double deadlineHours = p.getPlanningHorizonHours();
            if (p.getDeliveryDeadline() != null) {
                double fromReady = hoursFromReady(p.getReadyTime(), p.getDeliveryDeadline());
                deadlineHours = Math.min(deadlineHours, fromReady);
            }

            int deadlineSlot = (int) Math.floor(deadlineHours / period + EPS);
            if (deadlineSlot < 0) deadlineSlot = 0;
            if (deadlineSlot > horizon) deadlineSlot = horizon;
            return new TimeGrid(period, horizon, deadlineSlot);
        }

        boolean finalProcessingAllowed(int slot) {
            return slot + 1 <= deadlineSlot;
        }
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

    private static final class VariableIndex {
        final int variableCount;
        final int[][][] process;
        final int[][][] depart;
        final int[][][] ownedTrip;
        final int[][][] rentalTrip;
        final int[][] legTravelSlots;
        final int[] arcTravelSlots;
        final int[] arcMaxTrips;
        final Map<String, Integer> arcLookup;
        final int horizonSlots;

        private VariableIndex(int variableCount,
                              int[][][] process,
                              int[][][] depart,
                              int[][][] ownedTrip,
                              int[][][] rentalTrip,
                              int[][] legTravelSlots,
                              int[] arcTravelSlots,
                              int[] arcMaxTrips,
                              Map<String, Integer> arcLookup,
                              int horizonSlots) {
            this.variableCount = variableCount;
            this.process = process;
            this.depart = depart;
            this.ownedTrip = ownedTrip;
            this.rentalTrip = rentalTrip;
            this.legTravelSlots = legTravelSlots;
            this.arcTravelSlots = arcTravelSlots;
            this.arcMaxTrips = arcMaxTrips;
            this.arcLookup = arcLookup;
            this.horizonSlots = horizonSlots;
        }

        static VariableIndex build(
                NewPostProblem problem,
                List<ShipmentFlow> shipments,
                List<ArcInfo> arcs,
                TimeGrid grid,
                int vehicleCount
        ) {
            int count = 0;
            int[][][] process = new int[shipments.size()][][];
            int[][][] depart = new int[shipments.size()][][];
            int[][] legTravelSlots = new int[shipments.size()][];

            for (int s = 0; s < shipments.size(); s++) {
                int visits = shipments.get(s).getPath().size();
                int legs = visits - 1;
                process[s] = new int[visits][grid.horizonSlots];
                depart[s] = new int[legs][grid.horizonSlots];
                legTravelSlots[s] = new int[legs];
                for (int v = 0; v < visits; v++) {
                    for (int t = 0; t < grid.horizonSlots; t++) process[s][v][t] = count++;
                }
                for (int l = 0; l < legs; l++) {
                    int from = shipments.get(s).getPath().get(l);
                    int to = shipments.get(s).getPath().get(l + 1);
                    legTravelSlots[s][l] = ExactScheduleOptimizer.travelSlots(problem.getDistance()[from][to], problem.getSpeedKmPerHour(), grid.periodHours);
                    for (int t = 0; t < grid.horizonSlots; t++) depart[s][l][t] = count++;
                }
            }

            int[][][] owned = new int[arcs.size()][vehicleCount][grid.horizonSlots];
            int[][][] rental = new int[arcs.size()][vehicleCount][grid.horizonSlots];
            int[] arcTravel = new int[arcs.size()];
            int[] maxTrips = new int[arcs.size()];
            Map<String, Integer> lookup = new HashMap<>();

            double loadingFactor = problem.getLoadingFactor();
            for (int ai = 0; ai < arcs.size(); ai++) {
                ArcInfo arc = arcs.get(ai);
                lookup.put(arc.key.toString(), ai);
                arcTravel[ai] = ExactScheduleOptimizer.travelSlots(arc.distance, problem.getSpeedKmPerHour(), grid.periodHours);
                double arcLoad = 0.0;
                for (ShipmentFlow shipment : shipments) {
                    List<Integer> path = shipment.getPath();
                    for (int l = 0; l < path.size() - 1; l++) {
                        if (path.get(l) == arc.key.from && path.get(l + 1) == arc.key.to) arcLoad += shipment.getTonnage();
                    }
                }
                double minCapacity = Double.POSITIVE_INFINITY;
                for (VehicleType vehicle : problem.getVehicleTypes()) minCapacity = Math.min(minCapacity, vehicle.usableCapacity(loadingFactor));
                maxTrips[ai] = Math.max(1, (int) Math.ceil(arcLoad / minCapacity + EPS));

                for (int vt = 0; vt < vehicleCount; vt++) {
                    for (int t = 0; t < grid.horizonSlots; t++) {
                        owned[ai][vt][t] = count++;
                        rental[ai][vt][t] = count++;
                    }
                }
            }

            return new VariableIndex(count, process, depart, owned, rental,
                    legTravelSlots, arcTravel, maxTrips, lookup, grid.horizonSlots);
        }

        int process(int s, int visit, int slot) { return process[s][visit][slot]; }
        int depart(int s, int leg, int slot) { return depart[s][leg][slot]; }
        int ownedTrip(int arc, int vt, int slot) { return ownedTrip[arc][vt][slot]; }
        int rentalTrip(int arc, int vt, int slot) { return rentalTrip[arc][vt][slot]; }
        int travelSlots(int s, int leg) { return legTravelSlots[s][leg]; }
        int travelSlotsForArc(int arc) { return arcTravelSlots[arc]; }
        int maxTripsForArc(ArcInfo arc) { return arcMaxTrips[arcIndex(arc)]; }

        private int arcIndex(ArcInfo arc) {
            Integer i = arcLookup.get(arc.key.toString());
            if (i == null) throw new IllegalStateException("Unknown arc");
            return i;
        }

        boolean canDepartWithinHorizon(int s, int leg, int departureSlot) {
            return departureSlot + travelSlots(s, leg) <= inferHorizon();
        }

        boolean canTripFinishWithinHorizon(int arc, int vt, int departureSlot) {
            return departureSlot + arcTravelSlots[arc] <= inferHorizon();
        }

        private int inferHorizon() {
            return horizonSlots;
        }
    }

    private static int travelSlots(double distance, double speed, double periodHours) {
        double hours = distance / speed;
        return Math.max(1, (int) Math.ceil(hours / periodHours - EPS));
    }
}
