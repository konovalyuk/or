package com.mavan.or.algorithms.lp;

import com.mavan.or.algorithms.lp.SimplexResult.DualConstraint;
import com.mavan.or.algorithms.lp.SimplexResult.DualProblem;
import com.mavan.or.algorithms.lp.SimplexResult.DualVariable;
import com.mavan.or.algorithms.lp.SimplexResult.Iteration;
import com.mavan.or.models.LinearProgram;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Chapter4DualityTest {

    private final Simplex solver = new Simplex();

    @Test
    void reddyMikksDualUsesMethodTwo() {
        SimplexResult result = solver.solve(reddy(), Simplex.ALL_SLACK);
        DualProblem dual = result.dual();
        assertEquals("MINIMIZE", dual.sense());
        assertEquals(21, dual.objectiveValue(), 1e-6);
        assertEquals(List.of(24.0, 6.0, 1.0, 2.0), dual.variables().stream().map(DualVariable::objectiveCoefficient).toList());
        assertEquals(0.75, dual.variables().get(0).value(), 1e-6);
        assertEquals(0.5, dual.variables().get(1).value(), 1e-6);
        assertEquals(0, dual.variables().get(2).value(), 1e-6);
        assertEquals(0, dual.variables().get(3).value(), 1e-6);
        assertTrue(dual.variables().stream().allMatch(variable -> "NONNEGATIVE".equals(variable.sign())));
        assertEquals(List.of(6.0, 1.0, -1.0, 0.0), dual.constraints().get(0).coefficients());
        assertEquals("GE", dual.constraints().get(0).relation());
        assertEquals(5, dual.constraints().get(0).rhs(), 1e-6);
        assertEquals(List.of(4.0, 2.0, 1.0, 1.0), dual.constraints().get(1).coefficients());
        assertEquals(4, dual.constraints().get(1).rhs(), 1e-6);
    }

    @Test
    void example421ReportsMethodTwoPrices() {
        LinearProgram program = program("MAXIMIZE", new double[]{5, 12, 4},
                new double[][]{{1, 2, 1}, {2, -1, 3}},
                new String[]{"LE", "EQ"},
                new double[]{10, 8});
        SimplexResult result = solver.solve(program, Simplex.TWO_PHASE);
        assertEquals(Simplex.OPTIMAL, result.status());
        assertEquals(54.8, result.objectiveValue(), 1e-6);
        DualProblem dual = result.dual();
        assertEquals("MINIMIZE", dual.sense());
        assertEquals(54.8, dual.objectiveValue(), 1e-6);
        assertEquals("NONNEGATIVE", dual.variables().get(0).sign());
        assertEquals(29.0 / 5, dual.variables().get(0).value(), 1e-6);
        assertEquals("UNRESTRICTED", dual.variables().get(1).sign());
        assertEquals(-2.0 / 5, dual.variables().get(1).value(), 1e-6);
        DualConstraint second = dual.constraints().get(1);
        assertEquals(List.of(2.0, -1.0), second.coefficients());
        assertEquals("GE", second.relation());
        assertEquals(12, second.rhs(), 1e-6);
    }

    @Test
    void infeasibleSolveHasNoDual() {
        LinearProgram program = program("MAXIMIZE", new double[]{1}, new double[][]{{1}, {1}}, new String[]{"LE", "GE"}, new double[]{1, 2});
        SimplexResult result = solver.solve(program, Simplex.M_METHOD);
        assertEquals(Simplex.INFEASIBLE, result.status());
        assertNull(result.dual());
        assertNull(result.sensitivity());
    }

    @Test
    void generalizedSimplexSolvesExample442() {
        LinearProgram program = example442();
        assertEquals(List.of(Simplex.M_METHOD, Simplex.TWO_PHASE, Simplex.GENERALIZED), Simplex.applicable(program));
        SimplexResult generalized = solver.solve(program, Simplex.GENERALIZED);
        SimplexResult bigM = solver.solve(program, Simplex.M_METHOD);
        assertEquals(Simplex.OPTIMAL, generalized.status());
        assertEquals(bigM.objectiveValue(), generalized.objectiveValue(), 1e-6);
        assertEquals(bigM.variables().size(), generalized.variables().size());
        for (int index = 0; index < bigM.variables().size(); index++) {
            assertEquals(bigM.variables().get(index).value(), generalized.variables().get(index).value(), 1e-6);
        }
        Iteration first = generalized.iterations().get(0);
        assertEquals("GENERALIZED", first.phase());
        assertEquals("x2", first.columns().get(first.enteringColumn()));
        assertEquals("sx4", first.basic().get(first.leavingRow()));
        assertNull(first.rowRatios());
        assertNull(first.columnRatios());
        Iteration after = generalized.iterations().get(1);
        assertEquals(4, valueOf(after, "x2"), 1e-6);
    }

    @Test
    void dualFeasibleStartDoesNotOfferGeneralized() {
        LinearProgram program = program("MAXIMIZE", new double[]{-1}, new double[][]{{1}}, new String[]{"GE"}, new double[]{3});
        assertEquals(List.of(Simplex.DUAL_SIMPLEX, Simplex.M_METHOD, Simplex.TWO_PHASE), Simplex.applicable(program));
        assertEquals(List.of(Simplex.ALL_SLACK), Simplex.applicable(reddy()));
    }

    @Test
    void example451RightHandSidesSolvedDirectly() {
        SimplexResult larger = solver.solve(toyco(new double[]{3, 2, 5}, new double[]{600, 640, 590}), Simplex.ALL_SLACK);
        assertPlan(larger, 1880, 0, 140, 320);
        assertEquals(30, larger.constraints().get(2).deviation(), 1e-6);

        SimplexResult shifted = solver.solve(toyco(new double[]{3, 2, 5}, new double[]{450, 460, 400}), Simplex.ALL_SLACK);
        assertPlan(shifted, 1350, 0, 100, 230);
    }

    @Test
    void example452AddedConstraintSolvedDirectly() {
        SimplexResult redundant = solver.solve(toycoWith(new double[]{3, 1, 1}), Simplex.ALL_SLACK);
        assertPlan(redundant, 1350, 0, 100, 230);

        SimplexResult tight = solver.solve(toycoWith(new double[]{3, 3, 1}), Simplex.ALL_SLACK);
        assertPlan(tight, 1330, 0, 90, 230);
    }

    @Test
    void example453ObjectiveChangesSolvedDirectly() {
        SimplexResult mild = solver.solve(toyco(new double[]{2, 3, 4}, new double[]{430, 460, 420}), Simplex.ALL_SLACK);
        assertPlan(mild, 1220, 0, 100, 230);

        SimplexResult strong = solver.solve(toyco(new double[]{6, 3, 4}, new double[]{430, 460, 420}), Simplex.ALL_SLACK);
        assertPlan(strong, 1227.5, 10, 102.5, 215);
    }

    @Test
    void example454AddedActivitySolvedDirectly() {
        LinearProgram expanded = program("MAXIMIZE", new double[]{3, 2, 5, 4},
                new double[][]{{1, 2, 1, 1}, {3, 0, 2, 1}, {1, 4, 0, 2}},
                new String[]{"LE", "LE", "LE"},
                new double[]{430, 460, 420});
        SimplexResult result = solver.solve(expanded, Simplex.ALL_SLACK);
        assertEquals(Simplex.OPTIMAL, result.status());
        assertEquals(1465, result.objectiveValue(), 1e-6);
        assertEquals(0, result.variables().get(0).value(), 1e-6);
        assertEquals(0, result.variables().get(1).value(), 1e-6);
        assertEquals(125, result.variables().get(2).value(), 1e-6);
        assertEquals(210, result.variables().get(3).value(), 1e-6);
    }

    private static void assertPlan(SimplexResult result, double z, double x1, double x2, double x3) {
        assertTrue(Simplex.OPTIMAL.equals(result.status()) || Simplex.ALTERNATIVE_OPTIMA.equals(result.status()), result.status());
        assertEquals(z, result.objectiveValue(), 1e-6);
        assertEquals(x1, result.variables().get(0).value(), 1e-6);
        assertEquals(x2, result.variables().get(1).value(), 1e-6);
        assertEquals(x3, result.variables().get(2).value(), 1e-6);
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

    private static LinearProgram toyco(double[] objective, double[] rhs) {
        return program("MAXIMIZE", objective,
                new double[][]{{1, 2, 1}, {3, 0, 2}, {1, 4, 0}},
                new String[]{"LE", "LE", "LE"},
                rhs);
    }

    private static LinearProgram toycoWith(double[] extra) {
        return program("MAXIMIZE", new double[]{3, 2, 5},
                new double[][]{{1, 2, 1}, {3, 0, 2}, {1, 4, 0}, extra},
                new String[]{"LE", "LE", "LE", "LE"},
                new double[]{430, 460, 420, 500});
    }

    private static LinearProgram example442() {
        return program("MAXIMIZE", new double[]{0, 0, 2},
                new double[][]{{-1, 2, -2}, {-1, 1, 1}, {2, -1, 4}},
                new String[]{"GE", "LE", "LE"},
                new double[]{8, 4, 10});
    }

    private static LinearProgram program(String sense, double[] objective, double[][] coefficients, String[] relations, double[] rhs) {
        return LinearProgram.of(sense, objective, coefficients, relations, rhs);
    }
}
