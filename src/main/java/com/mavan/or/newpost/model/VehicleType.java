package com.mavan.or.newpost.model;

public final class VehicleType {
    private final String name;
    private final double nominalCapacity;
    private final double tariffPerKm;
    private final double rentalCostPerTrip;

    public VehicleType(String name, double nominalCapacity, double tariffPerKm, double rentalCostPerTrip) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Vehicle name is required");
        if (nominalCapacity <= 0 || tariffPerKm < 0 || rentalCostPerTrip < 0) {
            throw new IllegalArgumentException("Invalid vehicle parameters");
        }
        this.name = name;
        this.nominalCapacity = nominalCapacity;
        this.tariffPerKm = tariffPerKm;
        this.rentalCostPerTrip = rentalCostPerTrip;
    }

    public String getName() { return name; }
    public double getNominalCapacity() { return nominalCapacity; }
    public double getTariffPerKm() { return tariffPerKm; }
    public double getRentalCostPerTrip() { return rentalCostPerTrip; }

    public double usableCapacity(double loadingFactor) {
        if (loadingFactor <= 0 || loadingFactor > 1) throw new IllegalArgumentException("Loading factor must be in (0,1]");
        return nominalCapacity * loadingFactor;
    }
}
