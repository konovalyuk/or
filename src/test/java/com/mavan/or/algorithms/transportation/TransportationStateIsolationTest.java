package com.mavan.or.algorithms.transportation;

import com.mavan.or.algorithms.transportation.datamodels.TransResult;
import com.mavan.or.algorithms.transportation.datamodels.TransState;
import com.mavan.or.algorithms.transportation.datamodels.TransTable;
import com.mavan.or.algorithms.transportation.start.ETransBasicSolution;
import com.mavan.or.algorithms.transportation.start.LeastCost;
import com.mavan.or.algorithms.transportation.start.NorthwestCorner;
import com.mavan.or.algorithms.transportation.start.TransBasicSolution;
import com.mavan.or.algorithms.transportation.start.Vogel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransportationStateIsolationTest {

    private static final double[][] COSTS = {
            {10, 2, 20, 11},
            {12, 7, 9, 20},
            {4, 14, 16, 18}
    };
    private static final double[] SUPPLY = {15, 25, 10};
    private static final double[] DEMAND = {5, 15, 15, 15};

    @Test
    void statesKeepIndependentBeforeAndAfterPivotSnapshots() {
        TransResult result = solve();

        assertEquals(435.0, result.optimalCost(), TransTable.EPS);
        assertTrue(result.states().size() >= 2);

        boolean foundPivotChange = false;
        for (TransState state : result.states()) {
            assertNotNull(state.allocationBefore());
            assertNotNull(state.allocationAfter());
            if (!state.optimal()) {
                foundPivotChange = true;
                assertTrue(matricesDiffer(state.allocationBefore(), state.allocationAfter())
                        || basesDiffer(state.basicBefore(), state.basicAfter()));
            }
        }
        assertTrue(foundPivotChange);
    }

    @ParameterizedTest
    @EnumSource(ETransBasicSolution.class)
    void everyStartingMethodReachesSunRayOptimum(ETransBasicSolution method) {
        var basics = new TransBasicSolution(new NorthwestCorner(), new LeastCost(), new Vogel());
        var solver = new TransSolver(basics, new TransClosedLoop(), new TransPotentials());
        var table = new TransTable(COSTS, SUPPLY, DEMAND,
                new double[COSTS.length][COSTS[0].length],
                new boolean[COSTS.length][COSTS[0].length]);

        assertEquals(435.0, solver.solve(method, table).optimalCost(), TransTable.EPS);
    }

    @Test
    void resultAndStateAccessorsReturnDefensiveCopies() {
        TransResult result = solve();
        double optimalCell = result.optimalAllocation()[0][0];
        result.optimalAllocation()[0][0] = optimalCell + 999;
        assertEquals(optimalCell, result.optimalAllocation()[0][0], TransTable.EPS);

        TransState state = result.states().get(0);
        double beforeCell = state.allocationBefore()[0][0];
        double[][] returnedBefore = state.allocationBefore();
        returnedBefore[0][0] = beforeCell + 999;
        assertEquals(beforeCell, state.allocationBefore()[0][0], TransTable.EPS);

        double potential = state.u()[0];
        double[] returnedPotentials = state.u();
        returnedPotentials[0] = potential + 999;
        assertEquals(potential, state.u()[0], TransTable.EPS);
    }

    private TransResult solve() {
        var basics = new TransBasicSolution(new NorthwestCorner(), new LeastCost(), new Vogel());
        var solver = new TransSolver(basics, new TransClosedLoop(), new TransPotentials());
        var table = new TransTable(COSTS, SUPPLY, DEMAND,
                new double[COSTS.length][COSTS[0].length],
                new boolean[COSTS.length][COSTS[0].length]);
        return solver.solve(ETransBasicSolution.VOGEL_APPROXIMATION, table);
    }

    private boolean matricesDiffer(double[][] first, double[][] second) {
        for (int i = 0; i < first.length; i++) {
            for (int j = 0; j < first[i].length; j++) {
                if (Math.abs(first[i][j] - second[i][j]) > TransTable.EPS) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean basesDiffer(boolean[][] first, boolean[][] second) {
        for (int i = 0; i < first.length; i++) {
            for (int j = 0; j < first[i].length; j++) {
                if (first[i][j] != second[i][j]) {
                    return true;
                }
            }
        }
        return false;
    }
}
