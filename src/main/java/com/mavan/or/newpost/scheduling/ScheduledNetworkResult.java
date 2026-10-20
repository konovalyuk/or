package com.mavan.or.newpost.scheduling;

import java.util.List;
import java.util.Map;

/** Result of exact scheduling of a fixed static New Post routing plan. */
public final class ScheduledNetworkResult {
    public enum Status {
        OPTIMAL,
        FEASIBLE_NOT_PROVEN,
        INFEASIBLE,
        STATIC_PLAN_NOT_AVAILABLE
    }

    private final Status status;
    private final String message;
    private final boolean staticPlanOptimal;
    private final boolean globalRouteAndScheduleOptimal;
    private final double staticCost;
    private final double totalCost;
    private final double transportCost;
    private final double terminalCost;
    private final double rentalCost;
    private final double totalTonnage;
    private final double costPerTon;
    private final double completionHour;
    private final int completionSlot;
    private final int horizonSlots;
    private final double periodDurationHours;
    private final List<ScheduledVehicleTrip> vehicleTrips;
    private final List<TerminalOperation> terminalOperations;
    private final List<ShipmentLegSchedule> shipmentLegSchedules;
    private final Map<String, Double> arcTonnage;
    private final long searchNodes;

    public ScheduledNetworkResult(
            Status status,
            String message,
            boolean staticPlanOptimal,
            boolean globalRouteAndScheduleOptimal,
            double staticCost,
            double totalCost,
            double transportCost,
            double terminalCost,
            double rentalCost,
            double totalTonnage,
            double completionHour,
            int completionSlot,
            int horizonSlots,
            double periodDurationHours,
            List<ScheduledVehicleTrip> vehicleTrips,
            List<TerminalOperation> terminalOperations,
            List<ShipmentLegSchedule> shipmentLegSchedules,
            Map<String, Double> arcTonnage,
            long searchNodes
    ) {
        this.status = status;
        this.message = message;
        this.staticPlanOptimal = staticPlanOptimal;
        this.globalRouteAndScheduleOptimal = globalRouteAndScheduleOptimal;
        this.staticCost = staticCost;
        this.totalCost = totalCost;
        this.transportCost = transportCost;
        this.terminalCost = terminalCost;
        this.rentalCost = rentalCost;
        this.totalTonnage = totalTonnage;
        this.costPerTon = totalTonnage <= 1e-12 ? 0.0 : totalCost / totalTonnage;
        this.completionHour = completionHour;
        this.completionSlot = completionSlot;
        this.horizonSlots = horizonSlots;
        this.periodDurationHours = periodDurationHours;
        this.vehicleTrips = List.copyOf(vehicleTrips);
        this.terminalOperations = List.copyOf(terminalOperations);
        this.shipmentLegSchedules = List.copyOf(shipmentLegSchedules);
        this.arcTonnage = Map.copyOf(arcTonnage);
        this.searchNodes = searchNodes;
    }

    public Status getStatus() { return status; }
    public String getMessage() { return message; }
    public boolean isStaticPlanOptimal() { return staticPlanOptimal; }
    public boolean isGlobalRouteAndScheduleOptimal() { return globalRouteAndScheduleOptimal; }
    public double getStaticCost() { return staticCost; }
    public double getTotalCost() { return totalCost; }
    public double getTransportCost() { return transportCost; }
    public double getTerminalCost() { return terminalCost; }
    public double getRentalCost() { return rentalCost; }
    public double getTotalTonnage() { return totalTonnage; }
    public double getCostPerTon() { return costPerTon; }
    public double getCompletionHour() { return completionHour; }
    public int getCompletionSlot() { return completionSlot; }
    public int getHorizonSlots() { return horizonSlots; }
    public double getPeriodDurationHours() { return periodDurationHours; }
    public List<ScheduledVehicleTrip> getVehicleTrips() { return vehicleTrips; }
    public List<TerminalOperation> getTerminalOperations() { return terminalOperations; }
    public List<ShipmentLegSchedule> getShipmentLegSchedules() { return shipmentLegSchedules; }
    public Map<String, Double> getArcTonnage() { return arcTonnage; }
    public long getSearchNodes() { return searchNodes; }
}
