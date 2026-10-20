package com.mavan.or.newpost.example;

import com.mavan.or.newpost.model.NewPostProblem;
import com.mavan.or.newpost.model.NewPostResult;
import com.mavan.or.newpost.model.ShipmentFlow;
import com.mavan.or.newpost.model.VehicleAssignment;
import com.mavan.or.newpost.optimization.NetworkOptimizer;

public final class NewPostConsoleDemo {
    private NewPostConsoleDemo() {}

    public static void main(String[] args) {
        NewPostProblem problem = NewPostExample.documentedExample();
        NewPostResult result = new NetworkOptimizer().solve(problem);

        System.out.println("Status: " + result.getStatus());
        System.out.printf("Total cost: %.2f AED%n", result.getTotalCost());
        System.out.printf("Transport cost: %.2f AED%n", result.getTransportCost());
        System.out.printf("Terminal cost: %.2f AED%n", result.getTerminalCost());
        System.out.printf("Cost per ton: %.2f AED/t%n", result.getCostPerTon());
        System.out.printf("Total tonnage: %.2f t%n", result.getTotalTonnage());
        System.out.println("B&B nodes: " + result.getSearchNodes());

        System.out.println("\nShipments:");
        for (ShipmentFlow s : result.getShipments()) {
            System.out.printf("%.2f t: %s -> %s path=%s%n",
                    s.getTonnage(),
                    problem.getLocations().get(s.getOrigin()),
                    problem.getLocations().get(s.getDestination()),
                    s.getPath().stream().map(problem.getLocations()::get).toList());
        }

        System.out.println("\nVehicle assignments by arc:");
        for (VehicleAssignment a : result.getVehicleAssignments()) {
            System.out.printf("%s: trips=%d load=%.2f t utilisation=%.2f%% cost=%.2f%n",
                    problem.getVehicleTypes().get(a.getVehicleTypeIndex()).getName(),
                    a.getTripCount(), a.getLoadedTonnage(), a.getUtilizationPercent(), a.getTransportCost());
        }
    }
}
