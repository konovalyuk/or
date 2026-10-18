package com.mavan.or.algorithms.lp;

import com.mavan.or.algorithms.lp.SimplexResult.Iteration;
import com.mavan.or.algorithms.lp.SimplexResult.Range;
import com.mavan.or.models.LinearProgram;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimplexSolverTest {

    private final Simplex solver = new Simplex();

    @Test
    void reddyMikksMatchesTora() {
        SimplexResult result = solver.solve(reddy(), Simplex.ALL_SLACK);

        assertEquals(Simplex.OPTIMAL, result.status());
        assertEquals(21, result.objectiveValue(), 1e-6);
        assertEquals(3, result.variables().get(0).value(), 1e-6);
        assertEquals(1.5, result.variables().get(1).value(), 1e-6);
        assertEquals(15, result.variables().get(0).contribution(), 1e-6);
        assertEquals(6, result.variables().get(1).contribution(), 1e-6);
        assertEquals(0, result.constraints().get(0).deviation(), 1e-6);
        assertEquals(0, result.constraints().get(1).deviation(), 1e-6);
        assertEquals(2.5, result.constraints().get(2).deviation(), 1e-6);
        assertEquals(0.5, result.constraints().get(3).deviation(), 1e-6);
        assertEquals("SLACK", result.constraints().get(2).kind());

        assertEquals(3, result.iterations().size());
        Iteration first = result.iterations().get(0);
        assertEquals(0, first.enteringColumn());
        assertEquals(0, first.leavingRow());
        assertEquals("sx3", first.basic().get(0));
        assertEquals(-5, first.objectiveRow().get(0).constant(), 1e-6);
        assertEquals(-4, first.objectiveRow().get(1).constant(), 1e-6);
        assertEquals(4, first.rowRatios().get(0).value(), 1e-6);
        assertTrue(first.rowRatios().get(0).selected());
        assertEquals(6, first.rowRatios().get(1).value(), 1e-6);
        assertNull(first.rowRatios().get(2).value());
        assertNull(first.rowRatios().get(3).value());
        assertTrue(first.operations().get(0).contains("sx3"));
        assertTrue(first.operations().get(0).contains("6.00"));
        assertTrue(first.operations().get(1).contains("-5.00"));

        Iteration second = result.iterations().get(1);
        assertEquals(1, second.enteringColumn());
        assertEquals(1, second.leavingRow());
        assertEquals("sx4", second.basic().get(second.leavingRow()));
        assertEquals(1.5, second.rowRatios().get(second.leavingRow()).value(), 1e-6);

        Iteration optimal = result.iterations().get(2);
        assertTrue(optimal.optimal());
        assertNull(optimal.enteringColumn());
        assertEquals(21, optimal.objectiveRhs().constant(), 1e-6);
        assertEquals(3, valueOf(optimal, "x1"), 1e-6);
        assertEquals(1.5, valueOf(optimal, "x2"), 1e-6);
        assertEquals(2.5, valueOf(optimal, "sx5"), 1e-6);
        assertEquals(0.5, valueOf(optimal, "sx6"), 1e-6);

        Range x1 = result.sensitivity().variables().get(0);
        Range x2 = result.sensitivity().variables().get(1);
        assertEquals(5, x1.current(), 1e-6);
        assertEquals(2, x1.minimum(), 1e-6);
        assertEquals(6, x1.maximum(), 1e-6);
        assertEquals(0, x1.price(), 1e-6);
        assertEquals(4, x2.current(), 1e-6);
        assertEquals(10.0 / 3, x2.minimum(), 1e-6);
        assertEquals(10, x2.maximum(), 1e-6);
        assertEquals(0, x2.price(), 1e-6);

        Range c1 = result.sensitivity().constraints().get(0);
        Range c2 = result.sensitivity().constraints().get(1);
        Range c3 = result.sensitivity().constraints().get(2);
        Range c4 = result.sensitivity().constraints().get(3);
        assertEquals(20, c1.minimum(), 1e-6);
        assertEquals(36, c1.maximum(), 1e-6);
        assertEquals(0.75, c1.price(), 1e-6);
        assertEquals(4, c2.minimum(), 1e-6);
        assertEquals(20.0 / 3, c2.maximum(), 1e-6);
        assertEquals(0.5, c2.price(), 1e-6);
        assertEquals(-1.5, c3.minimum(), 1e-6);
        assertNull(c3.maximum());
        assertEquals(0, c3.price(), 1e-6);
        assertEquals(1.5, c4.minimum(), 1e-6);
        assertNull(c4.maximum());
        assertEquals(0, c4.price(), 1e-6);
    }

    @Test
    void applicableMethodsFollowTheStartingBasis() {
        assertEquals(java.util.List.of(Simplex.ALL_SLACK), Simplex.applicable(reddy()));
        assertEquals(java.util.List.of(Simplex.DUAL_SIMPLEX, Simplex.M_METHOD, Simplex.TWO_PHASE), Simplex.applicable(example441()));
        assertEquals(java.util.List.of(Simplex.M_METHOD, Simplex.TWO_PHASE, Simplex.GENERALIZED), Simplex.applicable(program("MAXIMIZE", new double[]{5}, new double[][]{{1}}, new String[]{"GE"}, new double[]{3})));
        assertEquals(java.util.List.of(Simplex.DUAL_SIMPLEX, Simplex.M_METHOD, Simplex.TWO_PHASE), Simplex.applicable(program("MAXIMIZE", new double[]{-1}, new double[][]{{1}}, new String[]{"GE"}, new double[]{3})));
        assertEquals(java.util.List.of(Simplex.ALL_SLACK), Simplex.applicable(program("MAXIMIZE", new double[]{1}, new double[][]{{-1}}, new String[]{"GE"}, new double[]{-4})));
        assertThrows(IllegalArgumentException.class, () -> solver.solve(reddy(), Simplex.DUAL_SIMPLEX));
    }

    @Test
    void bigMAndTwoPhaseAgreeOnAGreaterThanConstraint() {
        LinearProgram program = program("MAXIMIZE", new double[]{-1}, new double[][]{{1}}, new String[]{"GE"}, new double[]{3});
        for (String method : new String[]{Simplex.M_METHOD, Simplex.TWO_PHASE, Simplex.DUAL_SIMPLEX}) {
            SimplexResult result = solver.solve(program, method);
            assertEquals(Simplex.OPTIMAL, result.status(), method);
            assertEquals(-3, result.objectiveValue(), 1e-6, method);
            assertEquals(3, result.variables().get(0).value(), 1e-6, method);
        }
    }

    @Test
    void dualSimplexSolvesExample441() {
        SimplexResult dual = solver.solve(example441(), Simplex.DUAL_SIMPLEX);
        SimplexResult phase = solver.solve(example441(), Simplex.TWO_PHASE);
        SimplexResult bigM = solver.solve(example441(), Simplex.M_METHOD);
        for (SimplexResult result : new SimplexResult[]{dual, phase, bigM}) {
            assertEquals(Simplex.OPTIMAL, result.status(), result.message());
            assertEquals(4.5, result.objectiveValue(), 1e-6);
            assertEquals(0, result.variables().get(0).value(), 1e-6);
            assertEquals(1.5, result.variables().get(1).value(), 1e-6);
            assertEquals(1.5, result.variables().get(2).value(), 1e-6);
        }
    }

    @Test
    void reportsUnboundedInfeasibleAlternativeAndDegeneracy() {
        SimplexResult unbounded = solver.solve(program("MAXIMIZE", new double[]{1, 1}, new double[][]{{1, -1}}, new String[]{"LE"}, new double[]{1}), Simplex.ALL_SLACK);
        assertEquals(Simplex.UNBOUNDED, unbounded.status());
        assertNull(unbounded.sensitivity());
        assertTrue(unbounded.iterations().size() >= 2);

        LinearProgram infeasible = program("MAXIMIZE", new double[]{1}, new double[][]{{1}, {1}}, new String[]{"LE", "GE"}, new double[]{1, 3});
        assertEquals(Simplex.INFEASIBLE, solver.solve(infeasible, Simplex.M_METHOD).status());
        assertEquals(Simplex.INFEASIBLE, solver.solve(infeasible, Simplex.TWO_PHASE).status());
        assertNull(solver.solve(infeasible, Simplex.M_METHOD).sensitivity());

        SimplexResult alternative = solver.solve(program("MAXIMIZE", new double[]{0, 1},
                new double[][]{{6, 4}, {1, 2}, {-1, 1}, {0, 1}},
                new String[]{"LE", "LE", "LE", "LE"}, new double[]{24, 6, 1, 2}), Simplex.ALL_SLACK);
        assertEquals(Simplex.ALTERNATIVE_OPTIMA, alternative.status());
        assertEquals(2, alternative.objectiveValue(), 1e-6);
        assertEquals(2, alternative.variables().get(1).value(), 1e-6);
        assertTrue(alternative.iterations().size() > 2);
        Iteration last = alternative.iterations().get(alternative.iterations().size() - 1);
        assertEquals(2, last.objectiveRhs().constant(), 1e-6);
        assertTrue(last.optimal());

        SimplexResult degenerate = solver.solve(program("MAXIMIZE", new double[]{1}, new double[][]{{1}}, new String[]{"LE"}, new double[]{0}), Simplex.ALL_SLACK);
        assertEquals(Simplex.OPTIMAL, degenerate.status());
        assertTrue(degenerate.degenerate());
        assertEquals(0, degenerate.objectiveValue(), 1e-6);
    }

    private static double valueOf(Iteration iteration, String name) {
        for (int row = 0; row < iteration.basic().size(); row++) {
            if (iteration.basic().get(row).equals(name)) {
                return iteration.solution().get(row).constant();
            }
        }
        return 0;
    }

    private static LinearProgram reddy() {
        return program("MAXIMIZE", new double[]{5, 4},
                new double[][]{{6, 4}, {1, 2}, {-1, 1}, {0, 1}},
                new String[]{"LE", "LE", "LE", "LE"},
                new double[]{24, 6, 1, 2});
    }

    private static LinearProgram example441() {
        return program("MINIMIZE", new double[]{3, 2, 1},
                new double[][]{{3, 1, 1}, {-3, 3, 1}, {1, 1, 1}},
                new String[]{"GE", "GE", "LE"},
                new double[]{3, 6, 3});
    }

    private static LinearProgram program(String sense, double[] objective, double[][] coefficients, String[] relations, double[] rhs) {
        return LinearProgram.of(sense, objective, coefficients, relations, rhs);
    }
}
