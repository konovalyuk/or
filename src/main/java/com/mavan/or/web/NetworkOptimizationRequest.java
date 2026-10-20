package com.mavan.or.web;

import java.util.List;
import com.mavan.or.newpost.model.DiscountBand;

/**
 * JSON request for the route-and-fleet planning scenario.
 *
 * A null entry in ownedVehicles or rentalLimits means "no limit" for that vehicle type.
 */
public record NetworkOptimizationRequest(
        List<String> locations,
        double[][] demandMatrix,
        double[][] distanceMatrix,
        List<VehicleTypeRequest> vehicleTypes,
        Double loadingFactor,
        Double speedKmPerHour,
        double[] terminalCapacity,
        double[] terminalHandlingCost,
        String readyTime,
        Double planningHorizonHours,
        Double periodDurationHours,
        String deliveryDeadline,
        Integer[] ownedVehicles,
        boolean rentalEnabled,
        Integer[] rentalLimits,
        Boolean directOnly,
        Boolean consolidation,
        double[][] routeActivationCost,
        double[] vehicleActivationCost,
        List<DiscountBand> discountBands,
        String currency,
        Long nodeLimit,
        Integer maxVariables,
        Integer maxConstraints,
        Long maxSparseNonZeros
) {
    public record VehicleTypeRequest(
            String name,
            double nominalCapacity,
            double tariffPerKm,
            double rentalCostPerTrip
    ) {}
}
