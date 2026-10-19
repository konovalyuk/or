package com.mavan.or.service;

import com.mavan.or.algorithms.lp.GraphicalLpSolver;
import com.mavan.or.algorithms.lp.Simplex;
import com.mavan.or.algorithms.network.cpm.CriticalPath;
import com.mavan.or.algorithms.network.maximumflow.FordFulkerson;
import com.mavan.or.algorithms.network.minimumspanningtree.MinimalSpanningTree;
import com.mavan.or.algorithms.network.shortestpath.Dijkstra;
import com.mavan.or.algorithms.network.shortestpath.Floyd;
import com.mavan.or.algorithms.transportation.TransClosedLoop;
import com.mavan.or.algorithms.transportation.TransPotentials;
import com.mavan.or.algorithms.transportation.TransSolver;
import com.mavan.or.algorithms.transportation.datamodels.TransTable;
import com.mavan.or.algorithms.transportation.start.ETransBasicSolution;
import com.mavan.or.algorithms.transportation.start.LeastCost;
import com.mavan.or.algorithms.transportation.start.NorthwestCorner;
import com.mavan.or.algorithms.transportation.start.TransBasicSolution;
import com.mavan.or.algorithms.transportation.start.Vogel;
import com.mavan.or.web.GraphicalSolveRequest;
import com.mavan.or.web.TransportationSolveRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SolverServiceTest {

    @Test
    void balancesWithDummyDestinationAndProducesInitialSnapshots() {
        SolverService service = service();

        var result = service.transportationTask(new TransportationSolveRequest(
                new double[][]{{2, 4}, {3, 1}}, new double[]{10, 10}, new double[]{5, 10},
                ETransBasicSolution.NORTHWEST_CORNER));

        assertTrue(result.dummyAdded());
        assertEquals("DESTINATION", result.dummyType());
        assertEquals(3, result.demand().length);
        assertTrue(result.initialSteps().size() >= 2);
        assertEquals(20.0, result.supply()[0] + result.supply()[1], TransTable.EPS);
    }

    @Test
    void graphicalTaskRejectsMoreThanTwoVariables() {
        GraphicalSolveRequest request = new GraphicalSolveRequest(
                "MAXIMIZE", new double[]{1, 1, 1},
                new double[][]{{1, 1, 1}}, new String[]{"LE"}, new double[]{1});
        assertThrows(IllegalArgumentException.class, () -> service().graphicalTask(request));
    }

    private static SolverService service() {
        return new SolverService(new TransSolver(
                new TransBasicSolution(new NorthwestCorner(), new LeastCost(), new Vogel()),
                new TransClosedLoop(), new TransPotentials()), new GraphicalLpSolver(), new Simplex(),
                new MinimalSpanningTree(), new Dijkstra(), new Floyd(), new FordFulkerson(), new CriticalPath());
    }
}
