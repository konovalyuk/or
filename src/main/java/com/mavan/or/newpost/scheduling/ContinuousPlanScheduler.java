package com.mavan.or.newpost.scheduling;

import com.mavan.or.newpost.model.NewPostProblem;
import com.mavan.or.newpost.model.ShipmentFlow;
import com.mavan.or.newpost.model.VehicleType;

import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Builds a continuous-time schedule for a fixed routing plan and a fixed set
 * of physical truck trips.
 *
 * Every visited terminal processes each shipment flow once: at the origin
 * before the first departure, at every transshipment city between arrival and
 * the next departure, and at the destination after arrival. A terminal is a
 * single resource that processes at exactly its capacity in t/h, one job at a
 * time, so the processing rate can never exceed the capacity. A truck leaves
 * only after every load it carries has been processed at its departure city.
 *
 * The schedule never changes routes, loads or trip counts, so its cost equals
 * the cost of the supplied plan. Hours are measured from the ready time.
 */
public final class ContinuousPlanScheduler {
    private static final double EPS = 1e-9;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    /** A physical trip of the plan. A non-null commodity key restricts its loads to one OD pair. */
    public record TripSpec(int from, int to, int vehicleTypeIndex, Integer commodityKey) {}

    public ContinuousSchedule schedule(NewPostProblem problem, List<ShipmentFlow> shipments, List<TripSpec> tripSpecs) {
        int n = problem.locationCount();
        double[][] distance = problem.getDistance();
        double[] capacity = problem.getTerminalCapacityPerHour();
        double[] handling = problem.getTerminalHandlingCostPerTon();
        List<VehicleType> vehicles = problem.getVehicleTypes();
        double loadingFactor = problem.getLoadingFactor();
        List<String> violations = new ArrayList<>();

        List<TripBuilder> trips = new ArrayList<>();
        Map<Integer, List<TripBuilder>> tripsByArc = new HashMap<>();
        for (TripSpec spec : tripSpecs) {
            VehicleType vehicle = vehicles.get(spec.vehicleTypeIndex());
            TripBuilder trip = new TripBuilder(trips.size(), spec, vehicle.usableCapacity(loadingFactor));
            trips.add(trip);
            tripsByArc.computeIfAbsent(spec.from() * n + spec.to(), k -> new ArrayList<>()).add(trip);
        }
        for (List<TripBuilder> arcTrips : tripsByArc.values()) {
            arcTrips.sort(Comparator.comparingDouble((TripBuilder t) -> -t.usable).thenComparingInt(t -> t.index));
        }

        List<JobBuilder> jobs = new ArrayList<>();
        int[][] jobIndex = new int[shipments.size()][];
        for (int s = 0; s < shipments.size(); s++) {
            List<Integer> path = shipments.get(s).getPath();
            jobIndex[s] = new int[path.size()];
            for (int visit = 0; visit < path.size(); visit++) {
                PlannedTerminalJob.Kind kind = visit == 0 ? PlannedTerminalJob.Kind.ORIGIN
                        : visit == path.size() - 1 ? PlannedTerminalJob.Kind.DESTINATION
                        : PlannedTerminalJob.Kind.TRANSIT;
                jobIndex[s][visit] = jobs.size();
                jobs.add(new JobBuilder(jobs.size(), s, visit, path.get(visit), kind, shipments.get(s).getTonnage()));
            }
        }

        // Assign shipment legs to physical trips, earlier legs first.
        List<int[]> legs = new ArrayList<>();
        for (int s = 0; s < shipments.size(); s++) {
            List<Integer> path = shipments.get(s).getPath();
            for (int leg = 0; leg + 1 < path.size(); leg++) legs.add(new int[]{s, leg});
        }
        legs.sort(Comparator.<int[]>comparingInt(x -> x[1]).thenComparingInt(x -> x[0]));
        for (int[] legRef : legs) {
            int s = legRef[0];
            int leg = legRef[1];
            ShipmentFlow flow = shipments.get(s);
            int from = flow.getPath().get(leg);
            int to = flow.getPath().get(leg + 1);
            int key = flow.getOrigin() * n + flow.getDestination();
            double remaining = flow.getTonnage();
            for (TripBuilder trip : tripsByArc.getOrDefault(from * n + to, List.of())) {
                if (remaining <= EPS) break;
                if (trip.spec.commodityKey() != null && trip.spec.commodityKey() != key) continue;
                double q = Math.min(remaining, trip.usable - trip.load);
                if (q <= 1e-9) continue;
                trip.loads.add(new TripLoad(s, flow.getOrigin(), flow.getDestination(), leg, q));
                trip.load += q;
                remaining -= q;
                JobBuilder before = jobs.get(jobIndex[s][leg]);
                JobBuilder after = jobs.get(jobIndex[s][leg + 1]);
                if (!before.successorTrips.contains(trip.index)) {
                    before.successorTrips.add(trip.index);
                    trip.pendingJobs++;
                }
                if (!trip.successorJobs.contains(after.index)) {
                    trip.successorJobs.add(after.index);
                    after.pendingTrips++;
                }
            }
            if (remaining > 1e-6) {
                violations.add("Truck capacity on " + problem.getLocations().get(from) + " -> "
                        + problem.getLocations().get(to) + " is short by " + round(remaining) + " t");
            }
        }

        int[] ownedLimits = problem.getOwnedVehicleLimits();
        List<PriorityQueue<Double>> freeVehicles = new ArrayList<>();
        for (int vt = 0; vt < vehicles.size(); vt++) {
            PriorityQueue<Double> queue = new PriorityQueue<>();
            if (ownedLimits != null) for (int k = 0; k < ownedLimits[vt]; k++) queue.add(0.0);
            freeVehicles.add(queue);
        }

        double speed = problem.getSpeedKmPerHour();
        double[] terminalFree = new double[n];
        for (TripBuilder trip : trips) {
            if (trip.pendingJobs == 0) dispatch(trip, 0.0, distance, speed, ownedLimits, freeVehicles, jobs, vehicles, violations, problem);
        }

        int done = 0;
        while (done < jobs.size()) {
            JobBuilder next = null;
            double nextStart = Double.POSITIVE_INFINITY;
            for (JobBuilder job : jobs) {
                if (job.done || job.pendingTrips > 0) continue;
                double start = Math.max(job.release, terminalFree[job.city]);
                if (start < nextStart - EPS || (Math.abs(start - nextStart) <= EPS && next != null
                        && job.kind.ordinal() < next.kind.ordinal())) {
                    next = job;
                    nextStart = start;
                }
            }
            if (next == null) {
                violations.add("Circular consolidation dependency: no terminal job can start");
                break;
            }
            next.start = nextStart;
            next.end = nextStart + next.tonnage / capacity[next.city];
            next.done = true;
            terminalFree[next.city] = next.end;
            done++;
            for (int tripIndex : next.successorTrips) {
                TripBuilder trip = trips.get(tripIndex);
                trip.readyAt = Math.max(trip.readyAt, next.end);
                if (--trip.pendingJobs == 0) {
                    dispatch(trip, trip.readyAt, distance, speed, ownedLimits, freeVehicles, jobs, vehicles, violations, problem);
                }
            }
        }

        double completion = 0.0;
        List<PlannedTerminalJob> plannedJobs = new ArrayList<>();
        for (JobBuilder job : jobs) {
            if (!job.done) continue;
            plannedJobs.add(new PlannedTerminalJob(job.city, job.shipment, job.visit, job.kind, job.tonnage,
                    job.start, job.end, job.tonnage * handling[job.city]));
            if (job.kind == PlannedTerminalJob.Kind.DESTINATION) completion = Math.max(completion, job.end);
        }
        plannedJobs.sort(Comparator.comparingInt(PlannedTerminalJob::city).thenComparingDouble(PlannedTerminalJob::startHour));

        List<PlannedTrip> plannedTrips = new ArrayList<>();
        for (TripBuilder trip : trips) {
            VehicleType vehicle = vehicles.get(trip.spec.vehicleTypeIndex());
            double km = distance[trip.spec.from()][trip.spec.to()];
            plannedTrips.add(new PlannedTrip(trip.index, trip.spec.from(), trip.spec.to(), trip.spec.vehicleTypeIndex(),
                    vehicle.getName(), false, vehicle.getNominalCapacity(), trip.usable, trip.load, km,
                    trip.departure, trip.arrival, km * vehicle.getTariffPerKm(), 0.0, trip.loads));
        }
        plannedTrips.sort(Comparator.comparingDouble(PlannedTrip::departureHour).thenComparingInt(PlannedTrip::tripIndex));

        if (completion > problem.getPlanningHorizonHours() + 1e-9) {
            violations.add("Delivery completes after " + round(completion) + " h, beyond the "
                    + round(problem.getPlanningHorizonHours()) + " h planning horizon");
        }
        if (problem.getDeliveryDeadline() != null) {
            double deadline = hoursFromReady(problem.getReadyTime(), problem.getDeliveryDeadline());
            if (completion > deadline + 1e-9) {
                violations.add("Delivery completes " + round(completion) + " h after ready time, after the "
                        + problem.getDeliveryDeadline() + " deadline (" + round(deadline) + " h)");
            }
        }
        return new ContinuousSchedule(violations.isEmpty(), violations, plannedTrips, plannedJobs, completion);
    }

    private static void dispatch(TripBuilder trip, double readyAt, double[][] distance, double speed,
                                 int[] ownedLimits, List<PriorityQueue<Double>> freeVehicles,
                                 List<JobBuilder> jobs, List<VehicleType> vehicles,
                                 List<String> violations, NewPostProblem problem) {
        double departure = readyAt;
        int vt = trip.spec.vehicleTypeIndex();
        PriorityQueue<Double> queue = freeVehicles.get(vt);
        if (ownedLimits != null) {
            if (queue.isEmpty()) {
                violations.add("No owned " + vehicles.get(vt).getName() + " vehicle is available");
                departure = Double.POSITIVE_INFINITY;
            } else {
                departure = Math.max(departure, queue.poll());
            }
        }
        trip.departure = departure;
        trip.arrival = departure + distance[trip.spec.from()][trip.spec.to()] / speed;
        if (ownedLimits != null && Double.isFinite(trip.arrival)) queue.add(trip.arrival);
        for (int jobIndex : trip.successorJobs) {
            JobBuilder job = jobs.get(jobIndex);
            job.release = Math.max(job.release, trip.arrival);
            job.pendingTrips--;
        }
    }

    static double hoursFromReady(String readyText, String deadlineText) {
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

    private static String round(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static final class TripBuilder {
        final int index;
        final TripSpec spec;
        final double usable;
        final List<TripLoad> loads = new ArrayList<>();
        final List<Integer> successorJobs = new ArrayList<>();
        double load;
        int pendingJobs;
        double readyAt;
        double departure;
        double arrival;

        TripBuilder(int index, TripSpec spec, double usable) {
            this.index = index;
            this.spec = spec;
            this.usable = usable;
        }
    }

    private static final class JobBuilder {
        final int index;
        final int shipment;
        final int visit;
        final int city;
        final PlannedTerminalJob.Kind kind;
        final double tonnage;
        final List<Integer> successorTrips = new ArrayList<>();
        int pendingTrips;
        double release;
        double start;
        double end;
        boolean done;

        JobBuilder(int index, int shipment, int visit, int city, PlannedTerminalJob.Kind kind, double tonnage) {
            this.index = index;
            this.shipment = shipment;
            this.visit = visit;
            this.city = city;
            this.kind = kind;
            this.tonnage = tonnage;
        }
    }
}
