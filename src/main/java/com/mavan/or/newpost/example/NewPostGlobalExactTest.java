package com.mavan.or.newpost.example;

import com.mavan.or.newpost.global.GlobalExactNetworkOptimizer;
import com.mavan.or.newpost.global.GlobalExactNetworkResult;
import com.mavan.or.newpost.global.GlobalExactSettings;
import com.mavan.or.newpost.model.NewPostProblem;
import com.mavan.or.newpost.model.VehicleType;

import java.util.List;

/** Exact regression tests for the joint route + schedule model. */
public final class NewPostGlobalExactTest {
    public static void main(String[] args) {
        testDirectVsTransshipment();
        testFleetReuseAndDeadline();
        testRental();
        testTerminalDeadlineInfeasible();
        testConsolidation();
        testDirectOnly();
        testDeadlineChoosesFeasibleRoute();
        testFullDataSparseModel();
        System.out.println("Global exact New Post tests PASSED");
    }

    private static void testDirectVsTransshipment() {
        NewPostProblem p = NewPostProblem.builder()
                .locations(List.of("A", "B", "C"))
                .demand(new double[][]{{0, 4, 0}, {0, 0, 4}, {0, 0, 0}})
                .distance(new double[][]{{0, 10, 2}, {11, 0, 2}, {2, 3, 0}})
                .terminalCapacity(new double[]{100, 100, 100})
                .terminalHandlingCost(new double[]{0, 0, 0})
                .vehicleTypes(List.of(new VehicleType("20 t", 20, 1.0, 0)))
                .loadingFactor(0.8)
                .speedKmPerHour(60)
                .readyTime("20:00")
                .planningHorizonHours(5)
                .schedulePeriodDurationHours(1)
                .ownedVehicleLimits(new int[]{10})
                .build();
        GlobalExactNetworkResult r = new GlobalExactNetworkOptimizer().solve(p,
                new GlobalExactSettings(50_000, 10_000, 6_000));
        require(r.getStatus() == GlobalExactNetworkResult.Status.OPTIMAL, r.getMessage());
        require(r.getRoutes().stream().anyMatch(x -> x.getPath().size() > 2),
                "Expected transshipment route");
    }

    private static void testFleetReuseAndDeadline() {
        NewPostProblem p = NewPostProblem.builder()
                .locations(List.of("A", "B"))
                .demand(new double[][]{{0, 16}, {0, 0}})
                .distance(new double[][]{{0, 60}, {60, 0}})
                .terminalCapacity(new double[]{100, 100})
                .terminalHandlingCost(new double[]{0, 0})
                .vehicleTypes(List.of(new VehicleType("20 t", 20, 1.0, 0)))
                .loadingFactor(0.8)
                .speedKmPerHour(60)
                .readyTime("20:00")
                .planningHorizonHours(4)
                .schedulePeriodDurationHours(1)
                .deliveryDeadline("00:00")
                .ownedVehicleLimits(new int[]{1})
                .build();
        GlobalExactNetworkResult r = new GlobalExactNetworkOptimizer().solve(p,
                new GlobalExactSettings(100_000, 10_000, 6_000));
        require(r.getStatus() == GlobalExactNetworkResult.Status.OPTIMAL, r.getMessage());
        require(r.getVehicleTrips().size() == 1, "16 t must fit in one 20 t truck");
        require(r.getCompletionSlot() <= 4, "Deadline violation");
    }

    private static void testRental() {
        NewPostProblem p = NewPostProblem.builder()
                .locations(List.of("A", "B"))
                .demand(new double[][]{{0, 16}, {0, 0}})
                .distance(new double[][]{{0, 60}, {60, 0}})
                .terminalCapacity(new double[]{100, 100})
                .terminalHandlingCost(new double[]{0, 0})
                .vehicleTypes(List.of(new VehicleType("20 t", 20, 1.0, 25)))
                .loadingFactor(0.8)
                .speedKmPerHour(60)
                .readyTime("20:00")
                .planningHorizonHours(3)
                .schedulePeriodDurationHours(1)
                .deliveryDeadline("23:00")
                .ownedVehicleLimits(new int[]{0})
                .rentalEnabled(true)
                .rentalLimits(new int[]{1})
                .build();
        GlobalExactNetworkResult r = new GlobalExactNetworkOptimizer().solve(p,
                new GlobalExactSettings(100_000, 10_000, 6_000));
        require(r.getStatus() == GlobalExactNetworkResult.Status.OPTIMAL, r.getMessage());
        require(r.getRentalCost() > 0, "Expected rental cost");
        require(r.getVehicleTrips().stream().anyMatch(x -> x.isRented()), "Expected rented vehicle");
    }

    private static void testTerminalDeadlineInfeasible() {
        NewPostProblem p = NewPostProblem.builder()
                .locations(List.of("A", "B"))
                .demand(new double[][]{{0, 16}, {0, 0}})
                .distance(new double[][]{{0, 60}, {60, 0}})
                .terminalCapacity(new double[]{8, 8})
                .terminalHandlingCost(new double[]{0, 0})
                .vehicleTypes(List.of(new VehicleType("20 t", 20, 1.0, 0)))
                .loadingFactor(0.8)
                .speedKmPerHour(60)
                .readyTime("20:00")
                .planningHorizonHours(3)
                .schedulePeriodDurationHours(1)
                .deliveryDeadline("23:00")
                .ownedVehicleLimits(new int[]{1})
                .build();
        GlobalExactNetworkResult r = new GlobalExactNetworkOptimizer().solve(p,
                new GlobalExactSettings(100_000, 10_000, 6_000));
        require(r.getStatus() == GlobalExactNetworkResult.Status.INFEASIBLE,
                "Expected infeasible, got " + r.getStatus());
    }

    private static void testConsolidation() {
        NewPostProblem p = NewPostProblem.builder()
                .locations(List.of("A", "B", "C"))
                .demand(new double[][]{{0, 8, 0}, {0, 0, 8}, {0, 0, 0}})
                .distance(new double[][]{{0, 60, 60}, {60, 0, 60}, {60, 60, 0}})
                .terminalCapacity(new double[]{100, 100, 100})
                .terminalHandlingCost(new double[]{0, 0, 0})
                .vehicleTypes(List.of(new VehicleType("20 t", 20, 1.0, 0)))
                .loadingFactor(0.8)
                .speedKmPerHour(60)
                .readyTime("20:00")
                .planningHorizonHours(5)
                .schedulePeriodDurationHours(1)
                .deliveryDeadline("01:00")
                .ownedVehicleLimits(new int[]{2})
                .build();
        GlobalExactNetworkResult r = new GlobalExactNetworkOptimizer().solve(p,
                new GlobalExactSettings(150_000, 10_000, 6_000));
        require(r.getStatus() == GlobalExactNetworkResult.Status.OPTIMAL, r.getMessage());
        require(r.getRoutes().size() >= 2, "Expected both commodities");
    }

    private static void testDirectOnly() {
        NewPostProblem p = NewPostProblem.builder()
                .locations(List.of("A", "B", "C"))
                .demand(new double[][]{{0, 0, 4}, {0, 0, 0}, {0, 0, 0}})
                .distance(new double[][]{{0, 60, 300}, {60, 0, 60}, {300, 60, 0}})
                .terminalCapacity(new double[]{100, 100, 100})
                .terminalHandlingCost(new double[]{0, 0, 0})
                .vehicleTypes(List.of(new VehicleType("20 t", 20, 1.0, 0)))
                .loadingFactor(0.8)
                .speedKmPerHour(60)
                .readyTime("20:00")
                .planningHorizonHours(8)
                .schedulePeriodDurationHours(1)
                .deliveryDeadline("04:00")
                .ownedVehicleLimits(new int[]{1})
                .directOnly(true)
                .build();
        GlobalExactNetworkResult r = new GlobalExactNetworkOptimizer().solve(p,
                new GlobalExactSettings(50_000, 10_000, 6_000));
        require(r.getStatus() == GlobalExactNetworkResult.Status.OPTIMAL, r.getMessage());
        require(r.getRoutes().stream().allMatch(x -> x.getPath().size() == 2),
                "Direct-only mode must not use transshipment");
    }

    private static void testDeadlineChoosesFeasibleRoute() {
        NewPostProblem p = NewPostProblem.builder()
                .locations(List.of("A", "B", "C"))
                .demand(new double[][]{{0, 0, 8}, {0, 0, 0}, {0, 0, 0}})
                .distance(new double[][]{{0, 60, 300}, {60, 0, 60}, {300, 60, 0}})
                .terminalCapacity(new double[]{100, 100, 100})
                .terminalHandlingCost(new double[]{0, 0, 0})
                .vehicleTypes(List.of(new VehicleType("20 t", 20, 1.0, 0)))
                .loadingFactor(0.8)
                .speedKmPerHour(60)
                .readyTime("20:00")
                .planningHorizonHours(6)
                .schedulePeriodDurationHours(1)
                .deliveryDeadline("01:00")
                .ownedVehicleLimits(new int[]{1})
                .build();
        GlobalExactNetworkResult r = new GlobalExactNetworkOptimizer().solve(p,
                new GlobalExactSettings(100_000, 10_000, 6_000));
        require(r.getStatus() == GlobalExactNetworkResult.Status.OPTIMAL, r.getMessage());
        require(r.getRoutes().stream().allMatch(x -> x.getPath().equals(List.of(0, 1, 2))),
                "Hard deadline should force A->B->C");
        require(r.getCompletionSlot() <= 5, "Completion exceeds hard deadline");
    }

    private static void testFullDataSparseModel() {
        NewPostProblem p = NewPostExample.documentedExample();
        GlobalExactNetworkOptimizer optimizer = new GlobalExactNetworkOptimizer();
        GlobalExactNetworkOptimizer.ModelStatistics stats = optimizer.inspectModel(p);

        require(stats.horizonSlots() == 4, "Expected four one-hour slots");
        require(stats.variableCount() > 400, "Expected compact time-expanded model");
        require(stats.constraintCount() < 1_000, "Sparse flow formulation should remain compact in row count");
        require(stats.nonZeroCount() < 100_000L, "Sparse model should stay within the intended nonzero budget");
    }

    private static NewPostProblem base3(double[][] distance, double[][] demand) {
        return NewPostProblem.builder()
                .locations(List.of("A", "B", "C"))
                .demand(demand)
                .distance(distance)
                .terminalCapacity(new double[]{100, 100, 100})
                .terminalHandlingCost(new double[]{0, 0, 0})
                .vehicleTypes(List.of(
                        new VehicleType("20 t", 20, 1.0, 0),
                        new VehicleType("10 t", 10, 0.8, 0),
                        new VehicleType("5 t", 5, 0.5, 0)))
                .loadingFactor(0.8)
                .speedKmPerHour(60)
                .readyTime("20:00")
                .planningHorizonHours(8)
                .schedulePeriodDurationHours(1)
                .ownedVehicleLimits(new int[]{10, 10, 10})
                .build();
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
