package com.mavan.or.newpost.optimization;

import java.util.*;

/**
 * Exact branch-and-bound for MILP with continuous and integer variables.
 * LP relaxations are solved by the in-project two-phase simplex solver.
 */
final class MilpSolver {
    private static final double EPS = 1e-7;

    private final MilpModel model;
    private double bestObjective = Double.POSITIVE_INFINITY;
    private double[] bestSolution;
    private long visitedNodes;
    private long nodeLimit = 1_000_000L;
    private boolean nodeLimitReached;

    MilpSolver(MilpModel model) {
        this.model = model;
    }

    void setNodeLimit(long limit) {
        if (limit <= 0) throw new IllegalArgumentException("Node limit must be positive");
        nodeLimit = limit;
    }

    void setInitialIncumbent(double[] solution) {
        if (solution == null || solution.length != model.variableCount()) throw new IllegalArgumentException("Invalid incumbent");
        for (int i = 0; i < solution.length; i++) {
            if (solution[i] < -EPS || (Double.isFinite(model.upperBounds()[i]) && solution[i] > model.upperBounds()[i] + EPS)) {
                throw new IllegalArgumentException("Incumbent violates variable bounds at " + i);
            }
            if (model.integer()[i] && Math.abs(solution[i] - Math.rint(solution[i])) > EPS) {
                throw new IllegalArgumentException("Incumbent integer variable is fractional at " + i);
            }
        }
        for (LinearConstraint constraint : model.constraints()) {
            double lhs = 0.0;
            for (int i = 0; i < solution.length; i++) lhs += constraint.a[i] * solution[i];
            if (lhs > constraint.b + 1e-5) throw new IllegalArgumentException("Incumbent violates a constraint");
        }
        double objective = 0.0;
        for (int i = 0; i < solution.length; i++) objective += model.objectiveMin()[i] * solution[i];
        bestObjective = objective;
        bestSolution = solution.clone();
    }

    SolveResult solve() {
        List<LinearConstraint> base = new ArrayList<>(model.constraints());
        for (int i = 0; i < model.variableCount(); i++) {
            double ub = model.upperBounds()[i];
            if (Double.isFinite(ub)) {
                double[] a = new double[model.variableCount()];
                a[i] = 1.0;
                base.add(new LinearConstraint(a, ub));
            }
        }
        branch(base);
        if (bestSolution == null) return new SolveResult(false, false, Double.POSITIVE_INFINITY, null, visitedNodes);
        return new SolveResult(true, !nodeLimitReached, bestObjective, bestSolution.clone(), visitedNodes);
    }

    private void branch(List<LinearConstraint> constraints) {
        if (visitedNodes >= nodeLimit) {
            nodeLimitReached = true;
            return;
        }
        visitedNodes++;

        LpRelaxation relaxation = solveRelaxation(constraints);
        if (!relaxation.feasible || Double.isNaN(relaxation.objective)) return;

        if (relaxation.objective >= bestObjective - EPS) return;

        // Rounding every integer variable upward is a valid incumbent for
        // models where integer variables only relax resource-capacity
        // constraints. It is conservative but often gives a very strong
        // feasible upper bound before branching.
        double[] rounded = relaxation.x.clone();
        boolean roundedInteger = true;
        for (int i = 0; i < rounded.length; i++) {
            if (!model.integer()[i]) continue;
            rounded[i] = Math.ceil(rounded[i] - EPS);
            if (Double.isFinite(model.upperBounds()[i]) && rounded[i] > model.upperBounds()[i] + EPS) {
                roundedInteger = false;
                break;
            }
        }
        if (roundedInteger && satisfiesAllConstraints(rounded)) {
            double candidateObjective = objective(rounded);
            if (candidateObjective < bestObjective - EPS) {
                bestObjective = candidateObjective;
                bestSolution = rounded;
            }
        }

        int branchVar = chooseBranchVariable(relaxation.x);
        if (branchVar < 0) {
            bestObjective = relaxation.objective;
            bestSolution = relaxation.x.clone();
            return;
        }

        double value = relaxation.x[branchVar];
        double floor = Math.floor(value + EPS);
        double ceil = Math.ceil(value - EPS);

        if (ceil > floor) {
            List<LinearConstraint> left = new ArrayList<>(constraints);
            double[] a1 = new double[model.variableCount()];
            a1[branchVar] = 1.0;
            left.add(new LinearConstraint(a1, floor));
            branch(left);

            List<LinearConstraint> right = new ArrayList<>(constraints);
            double[] a2 = new double[model.variableCount()];
            a2[branchVar] = -1.0;
            right.add(new LinearConstraint(a2, -ceil));
            branch(right);
        }
    }

    private LpRelaxation solveRelaxation(List<LinearConstraint> constraints) {
        int m = constraints.size();
        int n = model.variableCount();
        double[][] a = new double[m][n];
        double[] b = new double[m];
        double[] objectiveMax = new double[n];

        for (int i = 0; i < m; i++) {
            System.arraycopy(constraints.get(i).a, 0, a[i], 0, n);
            b[i] = constraints.get(i).b;
        }
        for (int j = 0; j < n; j++) objectiveMax[j] = -model.objectiveMin()[j];

        double[] x = new double[n];
        double maxObjective = new LpSolver(a, b, objectiveMax).solve(x);
        if (Double.isNaN(maxObjective) || maxObjective == Double.POSITIVE_INFINITY) {
            return new LpRelaxation(false, Double.NaN, null);
        }
        return new LpRelaxation(true, -maxObjective, x);
    }


    private boolean satisfiesAllConstraints(double[] x) {
        for (LinearConstraint c : model.constraints()) {
            double lhs = 0.0;
            for (int i = 0; i < x.length; i++) lhs += c.a[i] * x[i];
            if (lhs > c.b + 1e-5) return false;
        }
        return true;
    }

    private double objective(double[] x) {
        double result = 0.0;
        for (int i = 0; i < x.length; i++) result += model.objectiveMin()[i] * x[i];
        return result;
    }

    private int chooseBranchVariable(double[] x) {
        int selected = -1;
        double bestFractionality = 0.0;

        for (int i = 0; i < x.length; i++) {
            if (!model.integer()[i]) continue;
            double nearest = Math.rint(x[i]);
            double fractionality = Math.abs(x[i] - nearest);
            if (fractionality > EPS && fractionality > bestFractionality) {
                bestFractionality = fractionality;
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

        SolveResult(boolean feasible, boolean provenOptimal, double objective, double[] solution, long visitedNodes) {
            this.feasible = feasible;
            this.provenOptimal = provenOptimal;
            this.objective = objective;
            this.solution = solution;
            this.visitedNodes = visitedNodes;
        }
    }

    private static final class LpRelaxation {
        final boolean feasible;
        final double objective;
        final double[] x;

        LpRelaxation(boolean feasible, double objective, double[] x) {
            this.feasible = feasible;
            this.objective = objective;
            this.x = x;
        }
    }
}
