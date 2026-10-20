package com.mavan.or.newpost.model;

import java.util.List;
import java.util.Map;

public final class NewPostResult {
    public enum Status { OPTIMAL, FEASIBLE_NOT_PROVEN, INFEASIBLE }

    private final Status status;
    private final String message;
    private final double totalCost;
    private final double transportCost;
    private final double terminalCost;
    private final double rentalCost;
    private final double activationCost;
    private final double totalTonnage;
    private final List<ShipmentFlow> shipments;
    private final List<VehicleAssignment> vehicleAssignments;
    private final List<VehicleTrip> vehicleTrips;
    private final Map<String, Double> arcTonnage;
    private final long searchNodes;

    public NewPostResult(Status status, String message, double totalCost, double transportCost,
                         double terminalCost, double rentalCost, double activationCost,
                         double totalTonnage, List<ShipmentFlow> shipments,
                         List<VehicleAssignment> vehicleAssignments, List<VehicleTrip> vehicleTrips,
                         Map<String, Double> arcTonnage, long searchNodes) {
        this.status = status;
        this.message = message;
        this.totalCost = totalCost;
        this.transportCost = transportCost;
        this.terminalCost = terminalCost;
        this.rentalCost = rentalCost;
        this.activationCost = activationCost;
        this.totalTonnage = totalTonnage;
        this.shipments = List.copyOf(shipments);
        this.vehicleAssignments = List.copyOf(vehicleAssignments);
        this.vehicleTrips = List.copyOf(vehicleTrips);
        this.arcTonnage = Map.copyOf(arcTonnage);
        this.searchNodes = searchNodes;
    }

    public Status getStatus() { return status; }
    public String getMessage() { return message; }
    public double getTotalCost() { return totalCost; }
    public double getTransportCost() { return transportCost; }
    public double getTerminalCost() { return terminalCost; }
    public double getRentalCost() { return rentalCost; }
    public double getActivationCost() { return activationCost; }
    public double getTotalTonnage() { return totalTonnage; }
    public double getCostPerTon() { return totalTonnage <= 1e-9 ? 0.0 : totalCost / totalTonnage; }
    public List<ShipmentFlow> getShipments() { return shipments; }
    public List<VehicleAssignment> getVehicleAssignments() { return vehicleAssignments; }
    public List<VehicleTrip> getVehicleTrips() { return vehicleTrips; }
    public Map<String, Double> getArcTonnage() { return arcTonnage; }
    public long getSearchNodes() { return searchNodes; }
}
