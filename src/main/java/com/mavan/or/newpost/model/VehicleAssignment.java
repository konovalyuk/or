package com.mavan.or.newpost.model;

public final class VehicleAssignment {
    private final int vehicleTypeIndex;
    private final int tripCount;
    private final double loadedTonnage;
    private final double nominalCapacity;
    private final double usableCapacity;
    private final double transportCost;

    public VehicleAssignment(int vehicleTypeIndex, int tripCount, double loadedTonnage,
                             double nominalCapacity, double usableCapacity, double transportCost) {
        this.vehicleTypeIndex = vehicleTypeIndex;
        this.tripCount = tripCount;
        this.loadedTonnage = loadedTonnage;
        this.nominalCapacity = nominalCapacity;
        this.usableCapacity = usableCapacity;
        this.transportCost = transportCost;
    }

    public int getVehicleTypeIndex() { return vehicleTypeIndex; }
    public int getTripCount() { return tripCount; }
    public double getLoadedTonnage() { return loadedTonnage; }
    public double getNominalCapacity() { return nominalCapacity; }
    public double getUsableCapacity() { return usableCapacity; }
    public double getTransportCost() { return transportCost; }
    public double getUtilizationPercent() {
        double totalCapacity = usableCapacity * tripCount;
        return totalCapacity <= 1e-9 ? 0.0 : loadedTonnage / totalCapacity * 100.0;
    }
}
