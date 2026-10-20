package com.mavan.or.newpost.optimization;

import java.util.*;

/** Exact branch-and-bound using the sparse LP relaxation backend. */
final class SparseMilpSolver {
    private static final double EPS = 1e-7;

    private final SparseMilpModel model;
    private double bestObjective = Double.POSITIVE_INFINITY;
    private double[] bestSolution;
    private long visitedNodes;
    private long nodeLimit = 1_000_000L;
    private boolean nodeLimitReached;

    SparseMilpSolver(SparseMilpModel model) {
        this.model = model;
    }

    void setNodeLimit(long limit) {
        if (limit <= 0) throw new IllegalArgumentException("Node limit must be positive");
        nodeLimit = limit;
    }

    void setInitialIncumbent(double[] solution) {
        if (solution == null || solution.length != model.variableCount) {
            throw new IllegalArgumentException("Invalid incumbent");
        }
        if (!satisfiesAllConstraints(solution, model.constraints)) {
            throw new IllegalArgumentException("Incumbent violates a constraint");
        }
        for (int i = 0; i < solution.length; i++) {
            if (solution[i] < -EPS) throw new IllegalArgumentException("Negative incumbent variable at " + i);
            double ub = model.upperBounds[i];
            if (Double.isFinite(ub) && solution[i] > ub + EPS) {
                throw new IllegalArgumentException("Incumbent violates upper bound at " + i);
            }
            if (model.integer[i] && Math.abs(solution[i] - Math.rint(solution[i])) > EPS) {
                throw new IllegalArgumentException("Incumbent integer variable is fractional at " + i);
            }
        }
        bestSolution = solution.clone();
        bestObjective = objective(solution);
    }

    SolveResult solve() {
        /*
         * Upper bounds are separated from the LP matrix and materialized
         * lazily.  This preserves exactness while avoiding thousands of
         * singleton rows for bounds that are inactive at the current LP
         * optimum.
         */
        List<SparseLinearConstraint> base = new ArrayList<>(model.constraints);
        branch(base);
        if (bestSolution == null) {
            return new SolveResult(false, false, Double.POSITIVE_INFINITY, null, visitedNodes);
        }
        return new SolveResult(true, !nodeLimitReached, bestObjective, bestSolution.clone(), visitedNodes);
    }

    private void branch(List<SparseLinearConstraint> constraints) {
        if (visitedNodes >= nodeLimit) {
            nodeLimitReached = true;
            return;
        }
        visitedNodes++;

        SparseLpSolver.Result relaxation = solveRelaxation(constraints);
        if (!relaxation.feasible() || Double.isNaN(relaxation.objective())) return;
        if (relaxation.objective() >= bestObjective - EPS) return;

        double[] rounded = relaxation.solution().clone();
        boolean roundedInteger = true;
        for (int i = 0; i < rounded.length; i++) {
            if (!model.integer[i]) continue;
            rounded[i] = Math.ceil(rounded[i] - EPS);
            if (Double.isFinite(model.upperBounds[i]) && rounded[i] > model.upperBounds[i] + EPS) {
                roundedInteger = false;
                break;
            }
        }
        if (roundedInteger && satisfiesAllConstraints(rounded, constraints)) {
            double candidate = objective(rounded);
            if (candidate < bestObjective - EPS) {
                bestObjective = candidate;
                bestSolution = rounded;
            }
        }

        int branchVar = chooseBranchVariable(relaxation.solution());
        if (branchVar < 0) {
            bestObjective = relaxation.objective();
            bestSolution = relaxation.solution().clone();
            return;
        }

        double value = relaxation.solution()[branchVar];
        double floor = Math.floor(value + EPS);
        double ceil = Math.ceil(value - EPS);
        if (ceil <= floor + EPS) return;

        List<SparseLinearConstraint> left = new ArrayList<>(constraints);
        left.add(new SparseLinearConstraint(new int[]{branchVar}, new double[]{1.0}, floor));
        branch(left);

        List<SparseLinearConstraint> right = new ArrayList<>(constraints);
        right.add(new SparseLinearConstraint(new int[]{branchVar}, new double[]{-1.0}, -ceil));
        branch(right);
    }

    private SparseLpSolver.Result solveRelaxation(List<SparseLinearConstraint> constraints) {
        List<SparseLinearConstraint> working = new ArrayList<>(constraints);
        boolean[] materializedUpperBounds = new boolean[model.variableCount];

        while (true) {
            SparseLpSolver solver = new SparseLpSolver(
                    model.variableCount, working, model.objectiveMin);
            SparseLpSolver.Result result = solver.solve();

            if (!result.feasible()) {
                if (!result.unbounded()) return result;

                // If the relaxation is unbounded, a finite model bound may be
                // essential to make it bounded. Materialize every remaining
                // finite bound exactly and retry.
                boolean addedAny = false;
                for (int i = 0; i < model.variableCount; i++) {
                    double ub = model.upperBounds[i];
                    if (Double.isFinite(ub) && !materializedUpperBounds[i]) {
                        working.add(new SparseLinearConstraint(
                                new int[]{i}, new double[]{1.0}, ub));
                        materializedUpperBounds[i] = true;
                        addedAny = true;
                    }
                }
                if (!addedAny) return result;
                continue;
            }

            double[] x = result.solution();
            boolean added = false;
            for (int i = 0; i < model.variableCount; i++) {
                double ub = model.upperBounds[i];
                if (!Double.isFinite(ub) || materializedUpperBounds[i]) continue;
                if (x[i] > ub + EPS) {
                    working.add(new SparseLinearConstraint(
                            new int[]{i}, new double[]{1.0}, ub));
                    materializedUpperBounds[i] = true;
                    added = true;
                }
            }
            if (!added) return result;
        }
    }

    private boolean satisfiesAllConstraints(double[] x, List<SparseLinearConstraint> constraints) {
        for (SparseLinearConstraint constraint : constraints) {
            double lhs = 0.0;
            for (int p = 0; p < constraint.size(); p++) {
                lhs += constraint.coefficientAt(p) * x[constraint.indexAt(p)];
            }
            if (lhs > constraint.rhs() + 1e-6) return false;
        }
        return true;
    }

    private double objective(double[] x) {
        double value = 0.0;
        for (int i = 0; i < model.variableCount; i++) value += model.objectiveMin[i] * x[i];
        return value;
    }

    private int chooseBranchVariable(double[] x) {
        int selected = -1;
        double bestFractionality = 0.0;
        for (int i = 0; i < x.length; i++) {
            if (!model.integer[i]) continue;
            double value = x[i];
            double fraction = Math.abs(value - Math.rint(value));
            if (fraction > EPS && fraction > bestFractionality) {
                bestFractionality = fraction;
                selected = i;
            }
        }
        return selected;
    }

    static final class SolveResult {
        final boolean feasible;
        final boolean provenOptimal;
        final double objective;
        final double[] solution;
        final long visitedNodes;

        SolveResult(boolean feasible, boolean provenOptimal, double objective,
                    double[] solution, long visitedNodes) {
            this.feasible = feasible;
            this.provenOptimal = provenOptimal;
            this.objective = objective;
            this.solution = solution;
            this.visitedNodes = visitedNodes;
        }
    }
}
