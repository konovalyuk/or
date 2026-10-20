package com.mavan.or.newpost.example;

import com.mavan.or.newpost.model.NewPostProblem;
import com.mavan.or.newpost.model.NewPostResult;
import com.mavan.or.newpost.model.ShipmentFlow;
import com.mavan.or.newpost.model.VehicleType;
import com.mavan.or.newpost.scheduling.ExactScheduleOptimizer;
import com.mavan.or.newpost.scheduling.ScheduledNetworkResult;
import com.mavan.or.newpost.scheduling.ScheduledVehicleTrip;
import com.mavan.or.newpost.scheduling.ScheduledNetworkOptimizer;

import java.util.List;
import java.util.Map;

/** Regression tests for the exact time-indexed scheduling layer. */
public final class NewPostSchedulingExactTest {
    public static void main(String[] args) {
        testFiniteFleetReuse();
        testRentalDecision();
        testDeadlineInfeasibilityFromTerminalCapacity();
        testConsolidationOnSharedLeg();
        testFullNewPostDataConfiguration();
        System.out.println("Exact New Post scheduling tests PASSED");
    }

    private static void testFiniteFleetReuse() {
        NewPostProblem p = NewPostProblem.builder()
                .locations(List.of("A", "B"))
                .demand(new double[][] {{0, 32}, {0, 0}})
                .distance(new double[][] {{0, 60}, {60, 0}})
                .terminalCapacity(new double[] {100, 100})
                .terminalHandlingCost(new double[] {0, 0})
                .vehicleTypes(List.of(new VehicleType("20 t", 20, 1.0, 0)))
                .loadingFactor(0.8)
                .speedKmPerHour(60)
                .readyTime("20:00")
                .planningHorizonHours(4)
                .deliveryDeadline("00:00")
                .ownedVehicleLimits(new int[] {1})
                .rentalEnabled(false)
                .build();

        NewPostResult staticPlan = new com.mavan.or.newpost.optimization.NetworkOptimizer().solve(
                staticCopy(p), 100_000
        );
        ScheduledNetworkResult r = new ExactScheduleOptimizer().solve(p, staticPlan, 100_000);

        require(r.getStatus() == ScheduledNetworkResult.Status.OPTIMAL, r.getMessage());
        require(r.getVehicleTrips().size() == 2, "Expected two truck trips");
        require(r.getCompletionSlot() <= 4, "Deadline/horizon violation");
    }

    private static void testRentalDecision() {
        NewPostProblem p = NewPostProblem.builder()
                .locations(List.of("A", "B"))
                .demand(new double[][] {{0, 16}, {0, 0}})
                .distance(new double[][] {{0, 60}, {60, 0}})
                .terminalCapacity(new double[] {100, 100})
                .terminalHandlingCost(new double[] {0, 0})
                .vehicleTypes(List.of(new VehicleType("20 t", 20, 1.0, 25)))
                .loadingFactor(0.8)
                .speedKmPerHour(60)
                .readyTime("20:00")
                .planningHorizonHours(3)
                .deliveryDeadline("23:00")
                .ownedVehicleLimits(new int[] {0})
                .rentalEnabled(true)
                .rentalLimits(new int[] {1})
                .build();

        NewPostResult staticPlan = new com.mavan.or.newpost.optimization.NetworkOptimizer().solve(
                staticCopy(p), 100_000
        );
        ScheduledNetworkResult r = new ExactScheduleOptimizer().solve(p, staticPlan, 100_000);

        require(r.getStatus() == ScheduledNetworkResult.Status.OPTIMAL, r.getMessage());
        require(r.getRentalCost() > 0, "Expected rental cost");
        require(r.getVehicleTrips().stream().anyMatch(ScheduledVehicleTrip::isRented), "Expected rented trip");
    }

    private static void testDeadlineInfeasibilityFromTerminalCapacity() {
        NewPostProblem p = NewPostProblem.builder()
                .locations(List.of("A", "B"))
                .demand(new double[][] {{0, 16}, {0, 0}})
                .distance(new double[][] {{0, 60}, {60, 0}})
                .terminalCapacity(new double[] {8, 8})
                .terminalHandlingCost(new double[] {0, 0})
                .vehicleTypes(List.of(new VehicleType("20 t", 20, 1.0, 0)))
                .loadingFactor(0.8)
                .speedKmPerHour(60)
                .readyTime("20:00")
                .planningHorizonHours(3)
                .deliveryDeadline("23:00")
                .ownedVehicleLimits(new int[] {1})
                .rentalEnabled(false)
                .build();

        NewPostResult staticPlan = new com.mavan.or.newpost.optimization.NetworkOptimizer().solve(
                staticCopy(p), 100_000
        );
        ScheduledNetworkResult r = new ExactScheduleOptimizer().solve(p, staticPlan, 100_000);

        require(r.getStatus() == ScheduledNetworkResult.Status.INFEASIBLE,
                "Expected infeasible schedule, got " + r.getStatus());
    }

    private static void testConsolidationOnSharedLeg() {
        NewPostProblem p = NewPostProblem.builder()
                .locations(List.of("A", "B", "C", "D"))
                .demand(new double[4][4])
                .distance(new double[][] {
                        {0, 60, 60, 100},
                        {60, 0, 60, 100},
                        {60, 60, 0, 60},
                        {100, 100, 60, 0}
                })
                .terminalCapacity(new double[] {100, 100, 100, 100})
                .terminalHandlingCost(new double[] {0, 0, 0, 0})
                .vehicleTypes(List.of(new VehicleType("20 t", 20, 1.0, 0)))
                .loadingFactor(0.8)
                .speedKmPerHour(60)
                .readyTime("20:00")
                .planningHorizonHours(7)
                .deliveryDeadline("03:00")
                .ownedVehicleLimits(new int[] {1})
                .rentalEnabled(false)
                .build();

        List<ShipmentFlow> shipments = List.of(
                new ShipmentFlow(0, 3, 8, List.of(0, 2, 3)),
                new ShipmentFlow(1, 3, 8, List.of(1, 2, 3))
        );
        NewPostResult staticPlan = new NewPostResult(
                NewPostResult.Status.OPTIMAL,
                "test fixed plan",
                0, 288, 0, 0, 0, 16,
                shipments, List.of(), List.of(), Map.of(), 0
        );

        ScheduledNetworkResult r = new ExactScheduleOptimizer().solve(p, staticPlan, 250_000);
        require(r.getStatus() == ScheduledNetworkResult.Status.OPTIMAL, r.getMessage());

        long sharedTrips = r.getVehicleTrips().stream()
                .filter(t -> t.getFrom() == 2 && t.getTo() == 3)
                .count();
        require(sharedTrips == 1, "Expected one consolidated C->D trip, got " + sharedTrips);
        ScheduledVehicleTrip shared = r.getVehicleTrips().stream()
                .filter(t -> t.getFrom() == 2 && t.getTo() == 3)
                .findFirst().orElseThrow();
        require(Math.abs(shared.getLoadedTonnage() - 16) < 1e-6,
                "Expected 16 t consolidated load, got " + shared.getLoadedTonnage());
    }

    private static void testFullNewPostDataConfiguration() {
        NewPostProblem p = NewPostExample.documentedExample();
        double total = 0.0;
        double[][] demand = p.getDemand();
        for (int i = 0; i < demand.length; i++) {
            for (int j = 0; j < demand[i].length; j++) total += demand[i][j];
        }
        require(Math.abs(total - 56.0) < 1e-6, "Expected 56 t in documented data");
        require(Math.abs(p.getSchedulePeriodDurationHours() - 1.0) < 1e-9, "Default scheduling period must be 1 h");
        require(Math.abs(p.getSpeedKmPerHour() - 60.0) < 1e-9, "Expected 60 km/h");
        require(p.getReadyTime().equals("20:00"), "Expected ready time 20:00");
        require(p.getVehicleTypes().get(0).usableCapacity(p.getLoadingFactor()) == 16.0, "20 t useful capacity");
        require(p.getVehicleTypes().get(1).usableCapacity(p.getLoadingFactor()) == 8.0, "10 t useful capacity");
        require(p.getVehicleTypes().get(2).usableCapacity(p.getLoadingFactor()) == 4.0, "5 t useful capacity");
    }

    private static NewPostProblem staticCopy(NewPostProblem p) {
        return NewPostProblem.builder()
                .locations(p.getLocations())
                .demand(p.getDemand())
                .distance(p.getDistance())
                .terminalCapacity(p.getTerminalCapacityPerHour())
                .terminalHandlingCost(p.getTerminalHandlingCostPerTon())
                .vehicleTypes(p.getVehicleTypes())
                .loadingFactor(p.getLoadingFactor())
                .speedKmPerHour(p.getSpeedKmPerHour())
                .readyTime(p.getReadyTime())
                .planningHorizonHours(p.getPlanningHorizonHours())
                .schedulePeriodDurationHours(p.getSchedulePeriodDurationHours())
                .directOnly(p.isDirectOnly())
                .build();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
