package com.mavan.or.newpost.global;

import com.mavan.or.newpost.scheduling.ScheduledVehicleTrip;
import com.mavan.or.newpost.scheduling.ShipmentLegSchedule;
import com.mavan.or.newpost.scheduling.TerminalOperation;

import java.util.List;
import java.util.Map;

/** Result of the joint route + fleet + terminal + schedule exact model. */
public final class GlobalExactNetworkResult {
    public enum Status {
        OPTIMAL,
        FEASIBLE_NOT_PROVEN,
        INFEASIBLE,
        SEARCH_LIMIT_REACHED_WITHOUT_FEASIBLE,
        MODEL_TOO_LARGE
    }

    private final Status status;
    private final String message;
    private final boolean globalOptimal;
    private final double totalCost;
    private final double transportCost;
    private final double terminalCost;
    private final double rentalCost;
    private final double activationCost;
    private final double totalTonnage;
    private final double costPerTon;
    private final double completionHour;
    private final int completionSlot;
    private final int horizonSlots;
    private final double periodDurationHours;
    private final List<GlobalShipmentRoute> routes;
    private final List<ShipmentLegSchedule> shipmentLegSchedules;
    private final List<ScheduledVehicleTrip> vehicleTrips;
    private final List<TerminalOperation> terminalOperations;
    private final Map<String, Double> arcTonnage;
    private final long searchNodes;
    private final int variableCount;
    private final int constraintCount;

    public GlobalExactNetworkResult(
            Status status,
            String message,
            boolean globalOptimal,
            double totalCost,
            double transportCost,
            double terminalCost,
            double rentalCost,
            double activationCost,
            double totalTonnage,
            double completionHour,
            int completionSlot,
            int horizonSlots,
            double periodDurationHours,
            List<GlobalShipmentRoute> routes,
            List<ShipmentLegSchedule> shipmentLegSchedules,
            List<ScheduledVehicleTrip> vehicleTrips,
            List<TerminalOperation> terminalOperations,
            Map<String, Double> arcTonnage,
            long searchNodes,
            int variableCount,
            int constraintCount
    ) {
        this.status = status;
        this.message = message;
        this.globalOptimal = globalOptimal;
        this.totalCost = totalCost;
        this.transportCost = transportCost;
        this.terminalCost = terminalCost;
        this.rentalCost = rentalCost;
        this.activationCost = activationCost;
        this.totalTonnage = totalTonnage;
        this.costPerTon = totalTonnage <= 1e-12 ? 0.0 : totalCost / totalTonnage;
        this.completionHour = completionHour;
        this.completionSlot = completionSlot;
        this.horizonSlots = horizonSlots;
        this.periodDurationHours = periodDurationHours;
        this.routes = List.copyOf(routes);
        this.shipmentLegSchedules = List.copyOf(shipmentLegSchedules);
        this.vehicleTrips = List.copyOf(vehicleTrips);
        this.terminalOperations = List.copyOf(terminalOperations);
        this.arcTonnage = Map.copyOf(arcTonnage);
        this.searchNodes = searchNodes;
        this.variableCount = variableCount;
        this.constraintCount = constraintCount;
    }

    public Status getStatus() { return status; }
    public String getMessage() { return message; }
    public boolean isGlobalOptimal() { return globalOptimal; }
    public double getTotalCost() { return totalCost; }
    public double getTransportCost() { return transportCost; }
    public double getTerminalCost() { return terminalCost; }
    public double getRentalCost() { return rentalCost; }
    public double getActivationCost() { return activationCost; }
    public double getTotalTonnage() { return totalTonnage; }
    public double getCostPerTon() { return costPerTon; }
    public double getCompletionHour() { return completionHour; }
    public int getCompletionSlot() { return completionSlot; }
    public int getHorizonSlots() { return horizonSlots; }
    public double getPeriodDurationHours() { return periodDurationHours; }
    public List<GlobalShipmentRoute> getRoutes() { return routes; }
    public List<ShipmentLegSchedule> getShipmentLegSchedules() { return shipmentLegSchedules; }
    public List<ScheduledVehicleTrip> getVehicleTrips() { return vehicleTrips; }
    public List<TerminalOperation> getTerminalOperations() { return terminalOperations; }
    public Map<String, Double> getArcTonnage() { return arcTonnage; }
    public long getSearchNodes() { return searchNodes; }
    public int getVariableCount() { return variableCount; }
    public int getConstraintCount() { return constraintCount; }
}
