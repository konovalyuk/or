package com.mavan.or.newpost.scheduling;

import java.util.List;

/** One physical truck trip with its consolidated loads and continuous-time schedule. */
public record PlannedTrip(
        int tripIndex,
        int from,
        int to,
        int vehicleTypeIndex,
        String vehicleName,
        boolean rented,
        double nominalCapacity,
        double usableCapacity,
        double loadedTonnage,
        double distance,
        double departureHour,
        double arrivalHour,
        double transportCost,
        double rentalCost,
        List<TripLoad> loads
) {
    public PlannedTrip {
        loads = List.copyOf(loads);
    }

    public double utilizationPercent() {
        return usableCapacity <= 1e-12 ? 0.0 : loadedTonnage / usableCapacity * 100.0;
    }
}
