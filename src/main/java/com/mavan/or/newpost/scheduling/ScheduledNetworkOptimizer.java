package com.mavan.or.newpost.scheduling;

import com.mavan.or.newpost.model.NewPostProblem;
import com.mavan.or.newpost.model.NewPostResult;
import com.mavan.or.newpost.model.VehicleType;
import com.mavan.or.newpost.optimization.NetworkOptimizer;

import java.util.List;

/**
 * Facade for the extended New Post scenario.
 *
 * The static routing optimizer is deliberately executed on a copy with
 * scheduling-only constraints removed. Its implementation is not modified.
 * The exact scheduler then solves the time/fleet/rental/deadline problem for
 * that fixed routing plan.
 */
public final class ScheduledNetworkOptimizer {
    private final NetworkOptimizer staticOptimizer = new NetworkOptimizer();
    private final ExactScheduleOptimizer scheduleOptimizer = new ExactScheduleOptimizer();

    public ScheduledNetworkResult solve(NewPostProblem problem) {
        return solve(problem, 1_000_000L, 500_000L);
    }

    public ScheduledNetworkResult solve(NewPostProblem problem, long staticNodeLimit, long scheduleNodeLimit) {
        NewPostProblem staticProblem = copyForStaticOptimization(problem);
        NewPostResult staticResult = staticOptimizer.solve(staticProblem, staticNodeLimit);
        if (staticResult.getStatus() == NewPostResult.Status.INFEASIBLE) {
            return new ScheduledNetworkResult(
                    ScheduledNetworkResult.Status.STATIC_PLAN_NOT_AVAILABLE,
                    "The static routing model is infeasible; scheduling cannot start.",
                    false,
                    false,
                    Double.POSITIVE_INFINITY,
                    Double.POSITIVE_INFINITY,
                    0.0,
                    0.0,
                    0.0,
                    staticResult.getTotalTonnage(),
                    Double.POSITIVE_INFINITY,
                    -1,
                    (int) Math.ceil(problem.getPlanningHorizonHours() / problem.getSchedulePeriodDurationHours()),
                    problem.getSchedulePeriodDurationHours(),
                    List.of(), List.of(), List.of(), java.util.Map.of(), staticResult.getSearchNodes()
            );
        }
        return scheduleOptimizer.solve(problem, staticResult, scheduleNodeLimit);
    }

    public ScheduledNetworkResult scheduleOnly(NewPostProblem problem, NewPostResult staticPlan, long scheduleNodeLimit) {
        return scheduleOptimizer.solve(problem, staticPlan, scheduleNodeLimit);
    }

    private static NewPostProblem copyForStaticOptimization(NewPostProblem p) {
        return NewPostProblem.builder()
                .locations(p.getLocations())
                .demand(p.getDemand())
                .distance(p.getDistance())
                .terminalCapacity(p.getTerminalCapacityPerHour())
                .terminalHandlingCost(p.getTerminalHandlingCostPerTon())
                .vehicleTypes(copyVehicles(p.getVehicleTypes()))
                .loadingFactor(p.getLoadingFactor())
                .speedKmPerHour(p.getSpeedKmPerHour())
                .readyTime(p.getReadyTime())
                .planningHorizonHours(p.getPlanningHorizonHours())
                .schedulePeriodDurationHours(p.getSchedulePeriodDurationHours())
                .directOnly(p.isDirectOnly())
                .rentalEnabled(false)
                .build();
    }

    private static List<VehicleType> copyVehicles(List<VehicleType> source) {
        return source.stream()
                .map(v -> new VehicleType(v.getName(), v.getNominalCapacity(), v.getTariffPerKm(), v.getRentalCostPerTrip()))
                .toList();
    }
}
