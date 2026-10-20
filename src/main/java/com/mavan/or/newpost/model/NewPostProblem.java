package com.mavan.or.newpost.model;

import java.util.*;

public final class NewPostProblem {
    private final List<String> locations;
    private final double[][] demand;
    private final double[][] distance;
    private final double[] terminalCapacityPerHour;
    private final double[] terminalHandlingCostPerTon;
    private final List<VehicleType> vehicleTypes;
    private final double loadingFactor;
    private final double speedKmPerHour;
    private final String readyTime;
    private final double planningHorizonHours;
    private final String deliveryDeadline;
    private final double schedulePeriodDurationHours;
    private final int[] ownedVehicleLimits;
    private final int[] rentalLimits;
    private final boolean rentalEnabled;
    private final boolean directOnly;
    private final double[][] routeActivationCost;
    private final double[] vehicleActivationCost;
    private final List<DiscountBand> discountBands;

    private NewPostProblem(Builder b) {
        this.locations = List.copyOf(b.locations);
        this.demand = copy(b.demand);
        this.distance = copy(b.distance);
        this.terminalCapacityPerHour = b.terminalCapacityPerHour.clone();
        this.terminalHandlingCostPerTon = b.terminalHandlingCostPerTon.clone();
        this.vehicleTypes = List.copyOf(b.vehicleTypes);
        this.loadingFactor = b.loadingFactor;
        this.speedKmPerHour = b.speedKmPerHour;
        this.readyTime = b.readyTime;
        this.planningHorizonHours = b.planningHorizonHours;
        this.deliveryDeadline = b.deliveryDeadline;
        this.schedulePeriodDurationHours = b.schedulePeriodDurationHours;
        this.ownedVehicleLimits = b.ownedVehicleLimits == null ? null : b.ownedVehicleLimits.clone();
        this.rentalLimits = b.rentalLimits == null ? null : b.rentalLimits.clone();
        this.rentalEnabled = b.rentalEnabled;
        this.directOnly = b.directOnly;
        this.routeActivationCost = copy(b.routeActivationCost);
        this.vehicleActivationCost = b.vehicleActivationCost == null ? null : b.vehicleActivationCost.clone();
        this.discountBands = List.copyOf(b.discountBands);
        validate();
    }

    public static Builder builder() { return new Builder(); }

    public Builder toBuilder() {
        return builder()
                .locations(locations)
                .demand(demand)
                .distance(distance)
                .terminalCapacity(terminalCapacityPerHour)
                .terminalHandlingCost(terminalHandlingCostPerTon)
                .vehicleTypes(vehicleTypes)
                .loadingFactor(loadingFactor)
                .speedKmPerHour(speedKmPerHour)
                .readyTime(readyTime)
                .planningHorizonHours(planningHorizonHours)
                .deliveryDeadline(deliveryDeadline)
                .schedulePeriodDurationHours(schedulePeriodDurationHours)
                .ownedVehicleLimits(ownedVehicleLimits)
                .rentalLimits(rentalLimits)
                .rentalEnabled(rentalEnabled)
                .directOnly(directOnly)
                .routeActivationCost(routeActivationCost)
                .vehicleActivationCost(vehicleActivationCost)
                .discountBands(discountBands);
    }

    public int locationCount() { return locations.size(); }
    public List<String> getLocations() { return locations; }
    public double[][] getDemand() { return copy(demand); }
    public double[][] getDistance() { return copy(distance); }
    public double[] getTerminalCapacityPerHour() { return terminalCapacityPerHour.clone(); }
    public double[] getTerminalHandlingCostPerTon() { return terminalHandlingCostPerTon.clone(); }
    public List<VehicleType> getVehicleTypes() { return vehicleTypes; }
    public double getLoadingFactor() { return loadingFactor; }
    public double getSpeedKmPerHour() { return speedKmPerHour; }
    public String getReadyTime() { return readyTime; }
    public double getPlanningHorizonHours() { return planningHorizonHours; }
    public String getDeliveryDeadline() { return deliveryDeadline; }
    public double getSchedulePeriodDurationHours() { return schedulePeriodDurationHours; }
    public int[] getOwnedVehicleLimits() { return ownedVehicleLimits == null ? null : ownedVehicleLimits.clone(); }
    public int[] getRentalLimits() { return rentalLimits == null ? null : rentalLimits.clone(); }
    public boolean isRentalEnabled() { return rentalEnabled; }
    public boolean isDirectOnly() { return directOnly; }
    public double[][] getRouteActivationCost() { return copy(routeActivationCost); }
    public double[] getVehicleActivationCost() { return vehicleActivationCost == null ? null : vehicleActivationCost.clone(); }
    public List<DiscountBand> getDiscountBands() { return discountBands; }

    public static final class Builder {
        private List<String> locations = new ArrayList<>();
        private double[][] demand;
        private double[][] distance;
        private double[] terminalCapacityPerHour;
        private double[] terminalHandlingCostPerTon;
        private List<VehicleType> vehicleTypes = new ArrayList<>();
        private double loadingFactor = 0.8;
        private double speedKmPerHour = 60.0;
        private String readyTime = "20:00";
        private double planningHorizonHours = 24.0;
        private String deliveryDeadline;
        private double schedulePeriodDurationHours = 1.0;
        private int[] ownedVehicleLimits;
        private int[] rentalLimits;
        private boolean rentalEnabled;
        private boolean directOnly;
        private double[][] routeActivationCost;
        private double[] vehicleActivationCost;
        private List<DiscountBand> discountBands = new ArrayList<>();

        public Builder locations(List<String> value) { locations = new ArrayList<>(value); return this; }
        public Builder demand(double[][] value) { demand = copy(value); return this; }
        public Builder distance(double[][] value) { distance = copy(value); return this; }
        public Builder terminalCapacity(double[] value) { terminalCapacityPerHour = value.clone(); return this; }
        public Builder terminalHandlingCost(double[] value) { terminalHandlingCostPerTon = value.clone(); return this; }
        public Builder vehicleTypes(List<VehicleType> value) { vehicleTypes = new ArrayList<>(value); return this; }
        public Builder loadingFactor(double value) { loadingFactor = value; return this; }
        public Builder speedKmPerHour(double value) { speedKmPerHour = value; return this; }
        public Builder readyTime(String value) { readyTime = Objects.requireNonNull(value); return this; }
        public Builder planningHorizonHours(double value) { planningHorizonHours = value; return this; }
        public Builder deliveryDeadline(String value) { deliveryDeadline = value; return this; }
        public Builder schedulePeriodDurationHours(double value) { schedulePeriodDurationHours = value; return this; }
        public Builder ownedVehicleLimits(int[] value) { ownedVehicleLimits = value == null ? null : value.clone(); return this; }
        public Builder rentalLimits(int[] value) { rentalLimits = value == null ? null : value.clone(); return this; }
        public Builder rentalEnabled(boolean value) { rentalEnabled = value; return this; }
        public Builder directOnly(boolean value) { directOnly = value; return this; }
        public Builder routeActivationCost(double[][] value) { routeActivationCost = copy(value); return this; }
        public Builder vehicleActivationCost(double[] value) { vehicleActivationCost = value == null ? null : value.clone(); return this; }
        public Builder discountBands(List<DiscountBand> value) { discountBands = value == null ? new ArrayList<>() : new ArrayList<>(value); return this; }

        public NewPostProblem build() { return new NewPostProblem(this); }
    }

    private void validate() {
        int n = locations.size();
        if (n == 0) throw new IllegalArgumentException("At least one location is required");
        if (demand == null || distance == null || demand.length != n || distance.length != n) {
            throw new IllegalArgumentException("Demand and distance matrices must match locations");
        }
        for (int i = 0; i < n; i++) {
            if (demand[i] == null || demand[i].length != n || distance[i] == null || distance[i].length != n) {
                throw new IllegalArgumentException("Demand and distance matrices must be square");
            }
            if (Math.abs(demand[i][i]) > 1e-9) throw new IllegalArgumentException("Demand diagonal must be zero");
            if (Math.abs(distance[i][i]) > 1e-9) throw new IllegalArgumentException("Distance diagonal must be zero");
            for (int j = 0; j < n; j++) {
                if (demand[i][j] < 0 || !Double.isFinite(demand[i][j])) throw new IllegalArgumentException("Invalid demand at ["+i+","+j+"]");
                if (distance[i][j] < 0 || !Double.isFinite(distance[i][j])) throw new IllegalArgumentException("Invalid distance at ["+i+","+j+"]");
            }
        }
        if (terminalCapacityPerHour == null || terminalCapacityPerHour.length != n) throw new IllegalArgumentException("Invalid terminal capacity");
        if (terminalHandlingCostPerTon == null || terminalHandlingCostPerTon.length != n) throw new IllegalArgumentException("Invalid terminal handling cost");
        if (vehicleTypes.isEmpty()) throw new IllegalArgumentException("At least one vehicle type is required");
        if (!(loadingFactor > 0 && loadingFactor <= 1)) throw new IllegalArgumentException("Loading factor must be in (0,1]");
        if (speedKmPerHour <= 0 || planningHorizonHours <= 0 || schedulePeriodDurationHours <= 0 || !Double.isFinite(schedulePeriodDurationHours)) throw new IllegalArgumentException("Speed, horizon and scheduling period must be positive and finite");
        for (double v : terminalCapacityPerHour) if (v <= 0) throw new IllegalArgumentException("Terminal capacity must be positive");
        for (double v : terminalHandlingCostPerTon) if (v < 0) throw new IllegalArgumentException("Terminal handling cost cannot be negative");
        for (VehicleType v : vehicleTypes) if (v.usableCapacity(loadingFactor) <= 0) throw new IllegalArgumentException("Vehicle usable capacity must be positive");
        if (ownedVehicleLimits != null) {
            if (ownedVehicleLimits.length != vehicleTypes.size()) throw new IllegalArgumentException("Owned fleet limits size mismatch");
            for (int value : ownedVehicleLimits) if (value < 0) throw new IllegalArgumentException("Owned fleet limits cannot be negative");
        }
        if (rentalLimits != null) {
            if (rentalLimits.length != vehicleTypes.size()) throw new IllegalArgumentException("Rental limits size mismatch");
            for (int value : rentalLimits) if (value < 0) throw new IllegalArgumentException("Rental limits cannot be negative");
        }
        if (routeActivationCost != null) {
            if (routeActivationCost.length != n) throw new IllegalArgumentException("Route activation matrix size mismatch");
            for (int i = 0; i < n; i++) {
                if (routeActivationCost[i] == null || routeActivationCost[i].length != n) throw new IllegalArgumentException("Route activation matrix must be square");
                for (double value : routeActivationCost[i]) if (value < 0 || !Double.isFinite(value)) throw new IllegalArgumentException("Invalid route activation cost");
            }
        }
        if (vehicleActivationCost != null) {
            if (vehicleActivationCost.length != vehicleTypes.size()) throw new IllegalArgumentException("Vehicle activation cost size mismatch");
            for (double value : vehicleActivationCost) if (value < 0 || !Double.isFinite(value)) throw new IllegalArgumentException("Invalid vehicle activation cost");
        }
    }

    private static double[][] copy(double[][] a) {
        if (a == null) return null;
        double[][] r = new double[a.length][];
        for (int i=0;i<a.length;i++) r[i]=a[i].clone();
        return r;
    }
}
