package com.mavan.or.newpost.model;


public final class VehicleTrip {
    private final int from;
    private final int to;
    private final int vehicleTypeIndex;
    private final double loadedTonnage;
    private final double distance;
    private final double departureHour;
    private final double arrivalHour;
    private final double cost;

    public VehicleTrip(int from, int to, int vehicleTypeIndex, double loadedTonnage,
                       double distance, double departureHour, double arrivalHour, double cost) {
        this.from = from;
        this.to = to;
        this.vehicleTypeIndex = vehicleTypeIndex;
        this.loadedTonnage = loadedTonnage;
        this.distance = distance;
        this.departureHour = departureHour;
        this.arrivalHour = arrivalHour;
        this.cost = cost;
    }

    public int getFrom() { return from; }
    public int getTo() { return to; }
    public int getVehicleTypeIndex() { return vehicleTypeIndex; }
    public double getLoadedTonnage() { return loadedTonnage; }
    public double getDistance() { return distance; }
    public double getDepartureHour() { return departureHour; }
    public double getArrivalHour() { return arrivalHour; }
    public double getCost() { return cost; }
}
