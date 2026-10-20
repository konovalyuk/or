package com.mavan.or.newpost.example;

import com.mavan.or.newpost.model.NewPostProblem;
import com.mavan.or.newpost.model.VehicleType;

import java.util.List;

public final class NewPostExample {
    private NewPostExample() {}

    public static NewPostProblem documentedExample() {
        List<String> cities = List.of(
                "Jebel Ali Port", "Dubai South / DWC", "DXB Cargo Village"
        );

        // A compact Dubai freight demonstration: 56 tonnes across three real
        // cargo hubs. Volumes, tariffs and terminal rates are illustrative
        // planning data, not figures of any operator.
        double[][] demand = {
                {0, 12, 8},
                {9, 0, 11},
                {6, 10, 0}
        };

        // Approximate directed road distances in km; diagonal = no self-route.
        // Road choice and access controls make the two directions differ.
        double[][] distance = {
                {0, 16, 40},
                {15, 0, 45},
                {39, 44, 0}
        };

        double[] terminalCapacity = {120, 100, 80};
        double[] terminalHandlingCost = {18, 20, 28};

        List<VehicleType> vehicles = List.of(
                new VehicleType("20 t", 20, 9.50, 0.0),
                new VehicleType("10 t", 10, 6.80, 0.0),
                new VehicleType("5 t", 5, 4.20, 0.0)
        );

        return NewPostProblem.builder()
                .locations(cities)
                .demand(demand)
                .distance(distance)
                .terminalCapacity(terminalCapacity)
                .terminalHandlingCost(terminalHandlingCost)
                .vehicleTypes(vehicles)
                .loadingFactor(0.8)
                .speedKmPerHour(60.0)
                .readyTime("20:00")
                .planningHorizonHours(4.0)
                .rentalEnabled(false)
                .directOnly(false)
                .build();
    }
}
