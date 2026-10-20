package com.mavan.or.newpost.optimization;

import java.util.*;

/**
 * Public sparse MILP backend for New Post.
 *
 * Unlike PublicMilpEngine, this backend never expands constraints into a
 * variableCount-wide dense tableau. LP relaxations use sparse pivot rows.
 */
public final class SparsePublicMilpEngine {
    private SparsePublicMilpEngine() {}

    public record Constraint(int[] indexes, double[] coefficients, double rhs) {
        public Constraint {
            if (indexes == null || coefficients == null || indexes.length != coefficients.length) {
                throw new IllegalArgumentException("Constraint arrays mismatch");
            }
        }

        public static Constraint fromMap(Map<Integer, Double> terms, double rhs) {
            List<Integer> indexes = new ArrayList<>();
            List<Double> values = new ArrayList<>();
            terms.forEach((key, value) -> {
                if (Math.abs(value) > 1e-12) {
                    indexes.add(key);
                    values.add(value);
                }
            });
            Integer[] sorted = indexes.toArray(Integer[]::new);
            Arrays.sort(sorted);
            int[] idx = new int[sorted.length];
            double[] coeff = new double[sorted.length];
            for (int i = 0; i < sorted.length; i++) {
                idx[i] = sorted[i];
                coeff[i] = terms.get(sorted[i]);
            }
            return new Constraint(idx, coeff, rhs);
        }
    }

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
        SparseMilpModel model = new SparseMilpModel(variableCount);
        System.arraycopy(objectiveMin, 0, model.objectiveMin, 0, variableCount);
        System.arraycopy(integer, 0, model.integer, 0, variableCount);
        System.arraycopy(upperBounds, 0, model.upperBounds, 0, variableCount);
        for (Constraint c : constraints) {
            model.constraints.add(new SparseLinearConstraint(c.indexes(), c.coefficients(), c.rhs()));
        }

        SparseMilpSolver solver = new SparseMilpSolver(model);
        solver.setNodeLimit(nodeLimit);
        if (incumbent != null) solver.setInitialIncumbent(incumbent);
        SparseMilpSolver.SolveResult result = solver.solve();
        return new SolveResult(
                result.feasible,
                result.provenOptimal,
                result.objective,
                result.solution == null ? null : result.solution.clone(),
                result.visitedNodes
        );
    }
}
