package com.mavan.or.newpost.scheduling;

/** A scheduled physical truck trip. */
public final class ScheduledVehicleTrip {
    private final int from;
    private final int to;
    private final int vehicleTypeIndex;
    private final boolean rented;
    private final double loadedTonnage;
    private final double nominalCapacity;
    private final double usableCapacity;
    private final double distance;
    private final int departureSlot;
    private final int arrivalSlot;
    private final double departureHour;
    private final double arrivalHour;
    private final double transportCost;
    private final double rentalCost;

    public ScheduledVehicleTrip(
            int from,
            int to,
            int vehicleTypeIndex,
            boolean rented,
            double loadedTonnage,
            double nominalCapacity,
            double usableCapacity,
            double distance,
            int departureSlot,
            int arrivalSlot,
            double departureHour,
            double arrivalHour,
            double transportCost,
            double rentalCost
    ) {
        this.from = from;
        this.to = to;
        this.vehicleTypeIndex = vehicleTypeIndex;
        this.rented = rented;
        this.loadedTonnage = loadedTonnage;
        this.nominalCapacity = nominalCapacity;
        this.usableCapacity = usableCapacity;
        this.distance = distance;
        this.departureSlot = departureSlot;
        this.arrivalSlot = arrivalSlot;
        this.departureHour = departureHour;
        this.arrivalHour = arrivalHour;
        this.transportCost = transportCost;
        this.rentalCost = rentalCost;
    }

    public int getFrom() { return from; }
    public int getTo() { return to; }
    public int getVehicleTypeIndex() { return vehicleTypeIndex; }
    public boolean isRented() { return rented; }
    public double getLoadedTonnage() { return loadedTonnage; }
    public double getNominalCapacity() { return nominalCapacity; }
    public double getUsableCapacity() { return usableCapacity; }
    public double getDistance() { return distance; }
    public int getDepartureSlot() { return departureSlot; }
    public int getArrivalSlot() { return arrivalSlot; }
    public double getDepartureHour() { return departureHour; }
    public double getArrivalHour() { return arrivalHour; }
    public double getTransportCost() { return transportCost; }
    public double getRentalCost() { return rentalCost; }
    public double getTotalCost() { return transportCost + rentalCost; }
    public double getUtilizationPercent() {
        return usableCapacity <= 1e-12 ? 0.0 : loadedTonnage / usableCapacity * 100.0;
    }
}
