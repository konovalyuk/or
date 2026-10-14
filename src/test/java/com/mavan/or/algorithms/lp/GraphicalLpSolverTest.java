package com.mavan.or.algorithms.lp;

import com.mavan.or.algorithms.lp.GraphicalResult.Point;
import com.mavan.or.algorithms.lp.GraphicalResult.Step;
import com.mavan.or.models.LinearProgram;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphicalLpSolverTest {

    private final GraphicalLpSolver solver = new GraphicalLpSolver();

    @Test
    void reddyMikksMaximizesAtCornerC() {
        GraphicalResult result = solver.solve(program("MAXIMIZE", new double[]{5, 4},
                new double[][]{{6, 4}, {1, 2}, {-1, 1}, {0, 1}},
                new String[]{"LE", "LE", "LE", "LE"},
                new double[]{24, 6, 1, 2}));

        assertEquals("OPTIMAL", result.status());
        assertEquals("POINT", result.optimumShape());
        assertEquals(3, result.x1(), 1e-6);
        assertEquals(1.5, result.x2(), 1e-6);
        assertEquals(21, result.objectiveValue(), 1e-6);
        assertTrue(contains(lastConstraint(result).polygon(), 3, 1.5));
        assertTrue(contains(lastConstraint(result).polygon(), 4, 0));
        assertTrue(contains(lastConstraint(result).polygon(), 0, 0));
        assertTrue(contains(lastConstraint(result).polygon(), 0, 1));
        assertTrue(contains(lastConstraint(result).polygon(), 1, 2));
        assertTrue(contains(lastConstraint(result).polygon(), 2, 2));
    }

    @Test
    void reddyMikksObjectiveCornersMatchTaha() {
        assertPoint(solveReddy(new double[]{5, 1}), 4, 0, 20);
        assertPoint(solveReddy(new double[]{1, 3}), 2, 2, 8);
        assertPoint(solveReddy(new double[]{-2, 1}), 0, 1, 1);
        assertPoint(solveReddy(new double[]{-1, -1}), 0, 0, 0);
    }

    @Test
    void reddyMikksHasAlternativeOptimaWhenObjectiveIsX2() {
        GraphicalResult result = solveReddy(new double[]{0, 1});
        assertEquals("ALTERNATIVE_OPTIMA", result.status());
        assertEquals("SEGMENT", result.optimumShape());
        assertEquals(2, result.objectiveValue(), 1e-6);
        assertNull(result.x1());
        assertTrue(contains(result.optimalPoints(), 1, 2));
        assertTrue(contains(result.optimalPoints(), 2, 2));
    }

    @Test
    void dietProblemMinimizesAtTheFeedMixCorner() {
        GraphicalResult result = solver.solve(program("MINIMIZE", new double[]{0.3, 0.9},
                new double[][]{{1, 1}, {0.21, -0.30}, {0.03, -0.01}},
                new String[]{"GE", "LE", "GE"},
                new double[]{800, 0, 0}));

        assertEquals("OPTIMAL", result.status());
        assertEquals(470.588235294, result.x1(), 1e-4);
        assertEquals(329.411764706, result.x2(), 1e-4);
        assertEquals(437.647058824, result.objectiveValue(), 1e-4);
    }

    @Test
    void reportsAnEmptyFeasibleRegion() {
        GraphicalResult result = solver.solve(program("MAXIMIZE", new double[]{1, 1},
                new double[][]{{1, 1}, {1, 1}},
                new String[]{"LE", "GE"},
                new double[]{1, 2}));
        assertEquals("INFEASIBLE", result.status());
        assertEquals("NONE", result.optimumShape());
        assertNull(result.objectiveValue());
        assertTrue(lastConstraint(result).polygon().isEmpty());
    }

    @Test
    void reportsAnUnboundedObjective() {
        GraphicalResult result = solver.solve(program("MAXIMIZE", new double[]{1, 0},
                new double[][]{{0, 1}},
                new String[]{"LE"},
                new double[]{1}));
        assertEquals("UNBOUNDED", result.status());
        assertNull(result.x1());
        assertNull(result.objectiveValue());
    }

    @Test
    void flatEdgeIsNotReportedAsASingleCorner() {
        GraphicalResult result = solver.solve(program("MAXIMIZE", new double[]{0, 1},
                new double[][]{{0, 1}},
                new String[]{"LE"},
                new double[]{2}));
        assertEquals("ALTERNATIVE_OPTIMA", result.status());
        assertEquals("RAY", result.optimumShape());
        assertEquals(2, result.objectiveValue(), 1e-6);
        assertTrue(contains(result.optimalPoints(), 0, 2));
    }

    @Test
    void equalityConstraintKeepsTheFeasibleSegment() {
        GraphicalResult result = solver.solve(program("MAXIMIZE", new double[]{1, 0},
                new double[][]{{1, 1}, {1, 0}, {0, 1}},
                new String[]{"EQ", "LE", "LE"},
                new double[]{4, 3, 3}));
        assertEquals("OPTIMAL", result.status());
        assertEquals(3, result.x1(), 1e-6);
        assertEquals(1, result.x2(), 1e-6);
        assertEquals(3, result.objectiveValue(), 1e-6);
    }

    private GraphicalResult solveReddy(double[] objective) {
        return solver.solve(program("MAXIMIZE", objective,
                new double[][]{{6, 4}, {1, 2}, {-1, 1}, {0, 1}},
                new String[]{"LE", "LE", "LE", "LE"},
                new double[]{24, 6, 1, 2}));
    }

    private static void assertPoint(GraphicalResult result, double x1, double x2, double z) {
        assertEquals("OPTIMAL", result.status());
        assertEquals(x1, result.x1(), 1e-6);
        assertEquals(x2, result.x2(), 1e-6);
        assertEquals(z, result.objectiveValue(), 1e-6);
    }

    private static Step lastConstraint(GraphicalResult result) {
        Step found = null;
        for (Step step : result.steps()) if ("CONSTRAINT".equals(step.kind())) found = step;
        return found;
    }

    private static boolean contains(List<Point> points, double x, double y) {
        for (Point point : points) {
            if (Math.abs(point.x() - x) <= 1e-4 && Math.abs(point.y() - y) <= 1e-4) return true;
        }
        return false;
    }

    private static LinearProgram program(String sense, double[] objective, double[][] coefficients, String[] relations, double[] rhs) {
        return LinearProgram.of(sense, objective, coefficients, relations, rhs);
    }
}
