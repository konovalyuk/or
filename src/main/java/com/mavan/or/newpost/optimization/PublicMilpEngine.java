package com.mavan.or.newpost.optimization;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Small public adapter around the internal MILP engine.
 *
 * Existing Classical/MODI code is not changed. New Post scheduling can use
 * the same in-project LP/MILP implementation without depending on package-
 * private internals.
 */
public final class PublicMilpEngine {
    private PublicMilpEngine() {}

    public record Constraint(double[] coefficients, double rhs) {}

    public record SolveResult(
            boolean feasible,
            boolean provenOptimal,
            double objective,
            double[] solution,
            long visitedNodes
    ) {}

    public static SolveResult solve(
            int variableCount,
            double[] objectiveMin,
            boolean[] integer,
            double[] upperBounds,
            List<Constraint> constraints,
            double[] incumbent,
            long nodeLimit
    ) {
        if (objectiveMin.length != variableCount
                || integer.length != variableCount
                || upperBounds.length != variableCount) {
            throw new IllegalArgumentException("MILP array size mismatch");
        }

        MilpModel model = new MilpModel(variableCount);
        for (int i = 0; i < variableCount; i++) {
            model.setObjective(i, objectiveMin[i]);
            if (integer[i]) model.setInteger(i);
            if (Double.isFinite(upperBounds[i])) model.setUpperBound(i, upperBounds[i]);
        }
        for (Constraint c : constraints) {
            if (c.coefficients().length != variableCount) {
                throw new IllegalArgumentException("Constraint dimension mismatch");
            }
            Map<Integer, Double> terms = new java.util.HashMap<>();
            for (int i = 0; i < variableCount; i++) {
                double a = c.coefficients()[i];
                if (Math.abs(a) > 1e-12) terms.put(i, a);
            }
            model.addLessEqual(terms, c.rhs());
        }

        MilpSolver solver = new MilpSolver(model);
        solver.setNodeLimit(nodeLimit);
        if (incumbent != null) solver.setInitialIncumbent(incumbent);
        MilpSolver.SolveResult r = solver.solve();
        return new SolveResult(
                r.feasible,
                r.provenOptimal,
                r.objective,
                r.solution == null ? null : r.solution.clone(),
                r.visitedNodes
        );
    }
}
