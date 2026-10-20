package com.mavan.or.newpost.optimization;

import com.mavan.or.newpost.model.NewPostProblem;
import com.mavan.or.newpost.model.VehicleType;
import com.mavan.or.newpost.scheduling.PlannedTerminalJob;
import com.mavan.or.newpost.scheduling.PlannedTrip;
import com.mavan.or.newpost.scheduling.TerminalLoad;
import com.mavan.or.newpost.scheduling.TripLoad;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Builds the step-by-step explanation of a plan from the solved numbers. */
final class PlanExplanation {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private PlanExplanation() {}

    static List<ExplanationSection> build(NewPostProblem p, ExactPlanResult r, String currency, boolean consolidation) {
        List<String> names = p.getLocations();
        double[][] demand = p.getDemand();
        double[][] distance = p.getDistance();
        double[] handling = p.getTerminalHandlingCostPerTon();
        double[] capacity = p.getTerminalCapacityPerHour();
        List<VehicleType> vehicles = p.getVehicleTypes();
        int n = p.locationCount();
        List<ExplanationSection> sections = new ArrayList<>();

        int flows = 0;
        for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) if (i != j && demand[i][j] > 1e-9) flows++;
        List<String> interpretation = new ArrayList<>();
        interpretation.add(n + " locations, " + flows + " origin-destination flows, " + num(r.totalTonnage()) + " t in total.");
        interpretation.add("Each non-zero demand cell is a separate commodity; it may go direct or through any other location (transshipment) and may be split across several routes.");
        StringBuilder fleet = new StringBuilder("Trucks: ");
        for (int k = 0; k < vehicles.size(); k++) {
            VehicleType v = vehicles.get(k);
            if (k > 0) fleet.append("; ");
            fleet.append(v.getName()).append(" carries ").append(num(v.usableCapacity(p.getLoadingFactor())))
                    .append(" t (").append(num(v.getNominalCapacity())).append(" t x ").append(num(p.getLoadingFactor()))
                    .append(") at ").append(num(v.getTariffPerKm())).append(' ').append(currency).append("/km");
        }
        interpretation.add(fleet + ".");
        interpretation.add("Objective: minimize transport (trips x km x tariff) + terminal handling (tonnes x tariff at the origin, every transshipment terminal and the destination)"
                + (p.isRentalEnabled() ? " + rental" : "") + ".");
        interpretation.add("Freight is ready at " + p.getReadyTime() + "; trucks drive at " + num(p.getSpeedKmPerHour())
                + " km/h; planning horizon " + ExactPlanService.hours(p.getPlanningHorizonHours())
                + (p.getDeliveryDeadline() == null ? "; no hard delivery deadline." : "; delivery deadline " + p.getDeliveryDeadline() + "."));
        interpretation.add(consolidation ? "Consolidation is allowed: one truck may carry several commodities on a shared leg."
                : "Consolidation is disabled: every commodity uses its own trucks.");
        interpretation.add(p.isDirectOnly() ? "Scenario: direct shipments only." : "Scenario: transshipment through any location is allowed.");
        sections.add(new ExplanationSection("1. Problem interpretation", interpretation));

        List<String> demandLines = new ArrayList<>();
        for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) {
            if (i != j && demand[i][j] > 1e-9) demandLines.add(names.get(i) + " -> " + names.get(j) + ": " + num(demand[i][j]) + " t");
        }
        sections.add(new ExplanationSection("2. Demand matrix", demandLines));

        List<String> graph = new ArrayList<>();
        for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) {
            if (i == j) continue;
            graph.add(names.get(i) + " -> " + names.get(j) + ": " + num(distance[i][j]) + " km, "
                    + ExactPlanService.hours(distance[i][j] / p.getSpeedKmPerHour()) + " driving");
        }
        sections.add(new ExplanationSection("3. Distance graph (directed)", graph));

        List<String> candidates = new ArrayList<>();
        SimplePathGenerator generator = new SimplePathGenerator(n);
        for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) {
            if (i == j || demand[i][j] <= 1e-9) continue;
            for (List<Integer> path : generator.generate(i, j)) {
                if (p.isDirectOnly() && path.size() > 2) continue;
                double km = 0.0;
                double perTon = 0.0;
                for (int k = 0; k < path.size(); k++) {
                    perTon += handling[path.get(k)];
                    if (k + 1 < path.size()) km += distance[path.get(k)][path.get(k + 1)];
                }
                candidates.add(path(names, path) + ": " + num(km) + " km, handling " + num(perTon) + " " + currency + "/t");
            }
        }
        sections.add(new ExplanationSection("4. Candidate routes", candidates));

        if (!r.hasPlan()) {
            List<String> none = new ArrayList<>(r.proof());
            none.addAll(r.warnings());
            sections.add(new ExplanationSection("5. No feasible plan", none));
            return sections;
        }

        Map<String, List<PlannedTrip>> tripsByArc = new LinkedHashMap<>();
        for (PlannedTrip trip : r.trips()) {
            tripsByArc.computeIfAbsent(names.get(trip.from()) + " -> " + names.get(trip.to()), k -> new ArrayList<>()).add(trip);
        }

        List<String> selected = new ArrayList<>();
        for (RoutePlan route : r.routes()) {
            String why;
            if (route.transshipment()) {
                List<String> shared = new ArrayList<>();
                for (int k = 0; k + 1 < route.path().size(); k++) {
                    String arc = names.get(route.path().get(k)) + " -> " + names.get(route.path().get(k + 1));
                    Set<String> partners = new TreeSet<>();
                    for (PlannedTrip trip : tripsByArc.getOrDefault(arc, List.of())) {
                        for (TripLoad load : trip.loads()) {
                            if (load.origin() != route.origin() || load.destination() != route.destination()) {
                                partners.add(names.get(load.origin()) + " -> " + names.get(load.destination()));
                            }
                        }
                    }
                    if (!partners.isEmpty()) shared.add("leg " + arc + " shares trucks with the " + String.join(", ", partners) + " flow");
                }
                why = shared.isEmpty()
                        ? "via transshipment: cheaper than the direct leg after truck rounding"
                        : "via transshipment: it fills spare truck capacity (" + String.join("; ", shared)
                        + "), which is cheaper than an extra direct truck even after the extra handling";
            } else {
                why = "direct: no cheaper combination through another terminal exists";
            }
            selected.add(path(names, route.path()) + ": " + num(route.tonnage()) + " t, " + num(route.distance()) + " km, "
                    + why + ".");
        }
        sections.add(new ExplanationSection("5. Selected routes", selected));

        List<String> capacityLines = new ArrayList<>();
        for (int k = 0; k < vehicles.size(); k++) {
            VehicleType v = vehicles.get(k);
            capacityLines.add(v.getName() + ": " + num(v.getTariffPerKm() / v.usableCapacity(p.getLoadingFactor()))
                    + " " + currency + " per km per usable tonne.");
        }
        for (Map.Entry<String, List<PlannedTrip>> entry : tripsByArc.entrySet()) {
            List<PlannedTrip> trips = entry.getValue();
            double load = trips.stream().mapToDouble(PlannedTrip::loadedTonnage).sum();
            double usable = trips.stream().mapToDouble(PlannedTrip::usableCapacity).sum();
            double cost = trips.stream().mapToDouble(t -> t.transportCost() + t.rentalCost()).sum();
            Map<String, Integer> mix = new TreeMap<>();
            for (PlannedTrip trip : trips) mix.merge(trip.vehicleName(), 1, Integer::sum);
            List<String> mixText = new ArrayList<>();
            mix.forEach((name, count) -> mixText.add(count + " x " + name));
            double km = trips.get(0).distance();
            List<String> alternatives = new ArrayList<>();
            for (VehicleType v : vehicles) {
                int count = (int) Math.ceil(load / v.usableCapacity(p.getLoadingFactor()) - 1e-9);
                alternatives.add(count + " x " + v.getName() + " = " + money(count * km * v.getTariffPerKm(), currency));
            }
            capacityLines.add(entry.getKey() + ": " + num(load) + " t on " + String.join(" + ", mixText)
                    + " (usable " + num(usable) + " t, " + num(usable <= 0 ? 0 : load / usable * 100) + "% loaded), "
                    + money(cost, currency) + "; single-type alternatives: " + String.join(", ", alternatives) + ".");
        }
        sections.add(new ExplanationSection("6. Vehicle capacity calculations", capacityLines));

        List<String> consolidationLines = new ArrayList<>();
        for (PlannedTrip trip : r.trips()) {
            Set<String> commodities = new TreeSet<>();
            for (TripLoad load : trip.loads()) commodities.add(names.get(load.origin()) + " -> " + names.get(load.destination()));
            if (commodities.size() > 1) {
                List<String> parts = new ArrayList<>();
                for (TripLoad load : trip.loads()) {
                    parts.add(num(load.tonnage()) + " t " + names.get(load.origin()) + " -> " + names.get(load.destination()));
                }
                consolidationLines.add("Trip " + (trip.tripIndex() + 1) + " " + names.get(trip.from()) + " -> " + names.get(trip.to())
                        + " (" + trip.vehicleName() + "): " + String.join(" + ", parts) + " = " + num(trip.loadedTonnage()) + " t.");
            }
        }
        if (consolidationLines.isEmpty()) consolidationLines.add("No truck carries more than one commodity in this plan.");
        sections.add(new ExplanationSection("7. Consolidation logic", consolidationLines));

        List<String> terminalLines = new ArrayList<>();
        for (TerminalLoad load : r.terminalLoads()) {
            terminalLines.add(names.get(load.city()) + ": outbound " + num(load.outboundTonnage()) + " t, transit "
                    + num(load.transitTonnage()) + " t, inbound " + num(load.inboundTonnage()) + " t = "
                    + num(load.totalTonnage()) + " t at " + num(capacity[load.city()]) + " t/h -> busy "
                    + ExactPlanService.hours(load.busyHours()) + " (" + num(load.utilizationPercent()) + "% of horizon), "
                    + num(load.totalTonnage()) + " t x " + num(handling[load.city()]) + " = " + money(load.cost(), currency) + ".");
        }
        sections.add(new ExplanationSection("8. Terminal processing", terminalLines));

        List<String> scheduleLines = new ArrayList<>();
        for (PlannedTrip trip : r.trips()) {
            scheduleLines.add(clock(p.getReadyTime(), trip.departureHour()) + " -> " + clock(p.getReadyTime(), trip.arrivalHour())
                    + "  " + names.get(trip.from()) + " -> " + names.get(trip.to()) + ", " + trip.vehicleName()
                    + (trip.rented() ? " (rental)" : "") + ", " + num(trip.loadedTonnage()) + " t");
        }
        if (r.completionHour() != null) {
            scheduleLines.add("All freight is processed at its destination by " + clock(p.getReadyTime(), r.completionHour())
                    + " (" + ExactPlanService.hours(r.completionHour()) + " after ready time).");
        }
        sections.add(new ExplanationSection("9. Delivery schedule", scheduleLines));

        List<String> cost = new ArrayList<>();
        cost.add("Transport: " + money(r.transportCost(), currency) + " for " + r.tripCount() + " trips, " + num(r.totalTruckKm()) + " truck-km.");
        cost.add("Terminal handling: " + money(r.terminalCost(), currency) + ".");
        if (r.rentalCost() > 0) cost.add("Rental: " + money(r.rentalCost(), currency) + ".");
        if (r.activationCost() > 0) cost.add("Activation: " + money(r.activationCost(), currency) + ".");
        cost.add("Total: " + money(r.totalCost(), currency) + " for " + num(r.totalTonnage()) + " t = "
                + money(r.costPerTon(), currency) + " per tonne.");
        cost.addAll(r.proof());
        sections.add(new ExplanationSection("10. Cost calculation", cost));

        List<String> plan = new ArrayList<>();
        long direct = r.routes().stream().filter(x -> !x.transshipment()).count();
        plan.add(direct + " direct route parts and " + (r.routes().size() - direct) + " transshipment route parts; "
                + num(r.transshipmentTonnage()) + " t pass through an intermediate terminal.");
        plan.add(r.tripCount() + " truck trips; total " + money(r.totalCost(), currency) + ", "
                + money(r.costPerTon(), currency) + " per tonne; status " + r.status() + ".");
        sections.add(new ExplanationSection("11. Final logistics plan", plan));
        return sections;
    }

    static String clock(String readyTime, double hoursFromReady) {
        LocalTime ready = LocalTime.parse(readyTime, TIME);
        long minutes = Math.round(hoursFromReady * 60.0);
        long total = ready.getHour() * 60L + ready.getMinute() + minutes;
        long day = total / (24 * 60);
        long inDay = total % (24 * 60);
        String text = String.format(Locale.ROOT, "%02d:%02d", inDay / 60, inDay % 60);
        return day > 0 ? text + " (+" + day + "d)" : text;
    }

    private static String path(List<String> names, List<Integer> path) {
        List<String> parts = new ArrayList<>();
        for (int city : path) parts.add(names.get(city));
        return String.join(" -> ", parts);
    }

    private static String money(double value, String currency) {
        return ExactPlanService.money(value, currency);
    }

    private static String num(double value) {
        return Math.abs(value - Math.rint(value)) < 1e-9
                ? String.format(Locale.ROOT, "%.0f", value)
                : String.format(Locale.ROOT, "%.2f", value);
    }
}
