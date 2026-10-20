package com.mavan.or.newpost.global;

import com.mavan.or.newpost.model.NewPostProblem;
import com.mavan.or.newpost.model.VehicleType;
import com.mavan.or.newpost.scheduling.TerminalOperation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class GlobalExactNetworkOptimizerTest {
    private static final double EPS = 1e-6;
    private static final GlobalExactSettings SETTINGS = new GlobalExactSettings(100_000, 100_000, 100_000, 10_000_000L);

    private final GlobalExactNetworkOptimizer optimizer = new GlobalExactNetworkOptimizer();

    @Test
    void transitTerminalHandlingCannotBeSkipped() {
        // A -> C costs 1000 by road, A -> B -> C only 2, but handling at B costs 1000 per tonne.
        NewPostProblem problem = threeCities(new double[][]{{0, 1, 1000}, {1, 0, 1}, {1000, 1, 0}},
                new double[]{100, 100, 100}, new double[]{10, 1000, 0}, 30);
        GlobalExactNetworkResult result = optimizer.solve(problem, SETTINGS);

        assertThat(result.getStatus()).isEqualTo(GlobalExactNetworkResult.Status.OPTIMAL);
        assertThat(result.getTotalCost()).isCloseTo(1040.0, within(EPS));
        assertThat(result.getTerminalCost()).isCloseTo(40.0, within(EPS));
        assertThat(result.getRoutes()).allSatisfy(route -> assertThat(route.getPath()).containsExactly(0, 2));
        double processedAtOrigin = result.getTerminalOperations().stream()
                .filter(op -> op.getCity() == 0).mapToDouble(TerminalOperation::getTonnage).sum();
        assertThat(processedAtOrigin).isCloseTo(4.0, within(EPS));
    }

    @Test
    void transitCargoMustBeProcessedWithinTerminalCapacity() {
        // Via B is cheaper, but B processes 0.5 t/h: 4 t need 8 slots, the horizon has 5.
        NewPostProblem problem = threeCities(new double[][]{{0, 1, 3}, {1, 0, 1}, {3, 1, 0}},
                new double[]{100, 0.5, 100}, new double[]{10, 0, 0}, 5);
        GlobalExactNetworkResult result = optimizer.solve(problem, SETTINGS);

        assertThat(result.getStatus()).isEqualTo(GlobalExactNetworkResult.Status.OPTIMAL);
        assertThat(result.getTotalCost()).isCloseTo(43.0, within(EPS));
        assertThat(result.getRoutes()).allSatisfy(route -> assertThat(route.getPath()).containsExactly(0, 2));
    }

    @Test
    void infeasibleHeuristicSeedDoesNotBreakTheSolver() {
        // The origin processes 1 t/h, so 4 t cannot leave within a 3 h horizon.
        NewPostProblem problem = threeCities(new double[][]{{0, 1, 3}, {1, 0, 1}, {3, 1, 0}},
                new double[]{1, 100, 100}, new double[]{10, 0, 0}, 3);
        GlobalExactNetworkResult result = optimizer.solve(problem, SETTINGS);

        assertThat(result.getStatus()).isEqualTo(GlobalExactNetworkResult.Status.INFEASIBLE);
    }

    private static NewPostProblem threeCities(double[][] distance, double[] capacity, double[] handling, double horizon) {
        return NewPostProblem.builder().locations(List.of("A", "B", "C"))
                .demand(new double[][]{{0, 0, 4}, {0, 0, 0}, {0, 0, 0}})
                .distance(distance)
                .terminalCapacity(capacity)
                .terminalHandlingCost(handling)
                .vehicleTypes(List.of(new VehicleType("20 t", 20, 1.0, 0)))
                .loadingFactor(0.8).planningHorizonHours(horizon).schedulePeriodDurationHours(1).build();
    }
}
