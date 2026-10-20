package com.mavan.or.web;

import com.mavan.or.newpost.example.NewPostExample;
import com.mavan.or.newpost.global.GlobalExactSettings;
import com.mavan.or.newpost.model.NewPostProblem;
import com.mavan.or.newpost.model.VehicleType;
import com.mavan.or.newpost.optimization.ExactPlanService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** HTTP adapter for the route, fleet, terminal and schedule planner. */
@RestController
@RequestMapping("/api/network-optimization")
public final class NetworkOptimizationController {
    private static final String DEFAULT_CURRENCY = "AED";

    private final ExactPlanService service = new ExactPlanService();

    @GetMapping("/example")
    public NetworkOptimizationRequest example() {
        return toRequest(NewPostExample.documentedExample());
    }

    @PostMapping("/solve")
    public NetworkOptimizationResponse solve(@RequestBody NetworkOptimizationRequest request) {
        NewPostProblem problem = toProblem(request);
        String currency = request.currency() == null || request.currency().isBlank() ? DEFAULT_CURRENCY : request.currency();
        ExactPlanService.Options options = new ExactPlanService.Options(
                !Boolean.FALSE.equals(request.consolidation()),
                2_000_000L,
                new GlobalExactSettings(
                        value(request.nodeLimit(), 500L),
                        value(request.maxVariables(), 100_000),
                        value(request.maxConstraints(), 50_000),
                        value(request.maxSparseNonZeros(), 10_000_000L)),
                currency);
        return NetworkOptimizationResponse.from(problem.getLocations(), currency, problem.getReadyTime(),
                service.solveWithScenarios(problem, options));
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(IllegalArgumentException.class)
    public Map<String, String> invalidProblem(IllegalArgumentException exception) {
        return Map.of("error", exception.getMessage() == null ? "Invalid route optimization request" : exception.getMessage());
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(IllegalStateException.class)
    public Map<String, String> verificationFailure(IllegalStateException exception) {
        return Map.of("error", "Solution verification failed: " + exception.getMessage());
    }

    private static NewPostProblem toProblem(NetworkOptimizationRequest r) {
        if (r == null || r.locations() == null || r.vehicleTypes() == null) {
            throw new IllegalArgumentException("locations and vehicleTypes are required");
        }
        if (r.discountBands() != null && !r.discountBands().isEmpty()) {
            throw new IllegalArgumentException("Volume discount bands are not supported by the exact planner.");
        }
        List<VehicleType> vehicles = r.vehicleTypes().stream()
                .map(v -> new VehicleType(v.name(), v.nominalCapacity(), v.tariffPerKm(), v.rentalCostPerTrip()))
                .toList();
        double loadingFactor = value(r.loadingFactor(), 0.8);
        return NewPostProblem.builder()
                .locations(r.locations())
                .demand(r.demandMatrix())
                .distance(r.distanceMatrix())
                .terminalCapacity(r.terminalCapacity())
                .terminalHandlingCost(r.terminalHandlingCost())
                .vehicleTypes(vehicles)
                .loadingFactor(loadingFactor)
                .speedKmPerHour(value(r.speedKmPerHour(), 60.0))
                .readyTime(r.readyTime() == null ? "20:00" : r.readyTime())
                .planningHorizonHours(value(r.planningHorizonHours(), 24.0))
                .schedulePeriodDurationHours(value(r.periodDurationHours(), 1.0))
                .deliveryDeadline(r.deliveryDeadline() == null || r.deliveryDeadline().isBlank() ? null : r.deliveryDeadline())
                .ownedVehicleLimits(limits(r.ownedVehicles(), vehicles, r, loadingFactor))
                .rentalEnabled(r.rentalEnabled())
                .rentalLimits(r.rentalEnabled() ? limits(r.rentalLimits(), vehicles, r, loadingFactor) : null)
                .directOnly(Boolean.TRUE.equals(r.directOnly()))
                .routeActivationCost(r.routeActivationCost())
                .vehicleActivationCost(r.vehicleActivationCost())
                .build();
    }

    /** All-null limits mean an unlimited fleet; a single null entry is replaced by a bound no plan can exceed. */
    private static int[] limits(Integer[] values, List<VehicleType> vehicles, NetworkOptimizationRequest r, double loadingFactor) {
        if (values == null) return null;
        if (values.length != vehicles.size()) throw new IllegalArgumentException("Fleet limits must match vehicle types");
        boolean anyLimit = false;
        for (Integer value : values) anyLimit |= value != null;
        if (!anyLimit) return null;
        double tonnage = 0.0;
        if (r.demandMatrix() != null) for (double[] row : r.demandMatrix()) for (double v : row) tonnage += v;
        double minUsable = vehicles.stream().mapToDouble(v -> v.usableCapacity(loadingFactor)).min().orElse(1.0);
        int arcs = r.locations().size() * Math.max(0, r.locations().size() - 1);
        int unlimited = (int) Math.ceil(tonnage / minUsable) * Math.max(1, arcs) + 1;
        int[] result = new int[values.length];
        for (int i = 0; i < values.length; i++) {
            if (values[i] != null && values[i] < 0) throw new IllegalArgumentException("Fleet limits cannot be negative");
            result[i] = values[i] == null ? unlimited : values[i];
        }
        return result;
    }

    private static NetworkOptimizationRequest toRequest(NewPostProblem p) {
        return new NetworkOptimizationRequest(
                p.getLocations(), p.getDemand(), p.getDistance(),
                p.getVehicleTypes().stream().map(v -> new NetworkOptimizationRequest.VehicleTypeRequest(
                        v.getName(), v.getNominalCapacity(), v.getTariffPerKm(), v.getRentalCostPerTrip())).toList(),
                p.getLoadingFactor(), p.getSpeedKmPerHour(), p.getTerminalCapacityPerHour(),
                p.getTerminalHandlingCostPerTon(), p.getReadyTime(), p.getPlanningHorizonHours(),
                p.getSchedulePeriodDurationHours(), p.getDeliveryDeadline(), null,
                p.isRentalEnabled(), null, p.isDirectOnly(), true,
                null, null, null, DEFAULT_CURRENCY,
                500L, 100_000, 50_000, 10_000_000L);
    }

    private static int value(Integer value, int fallback) { return value == null ? fallback : value; }
    private static long value(Long value, long fallback) { return value == null ? fallback : value; }
    private static double value(Double value, double fallback) { return value == null ? fallback : value; }
}
