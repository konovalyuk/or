package com.mavan.or.newpost.optimization;

import java.util.*;

/**
 * Revised primal simplex for sparse LP relaxations.
 *
 * Problem form:
 *
 *   minimize c^T x
 *   subject to A x <= b, x >= 0.
 *
 * The solver keeps the constraint matrix sparse and represents the inverse
 * basis by a product of eta transformations. Unlike the old dense tableau
 * implementation, it never allocates an m x n tableau.
 */
final class SparseLpSolver {
    private static final double EPS = 1e-8;
    private static final double CLEAN_EPS = 1e-10;
    private static final int MAX_PIVOTS = 100_000;
    private static final double INF = Double.POSITIVE_INFINITY;

    private final int rowCount;
    private final int variableCount;
    private final List<SparseLinearConstraint> constraints;
    private final double[] objectiveMin;

    private int[][] columnRows;
    private double[][] columnValues;

    private final int slackStart;
    private final int artificialStart;
    private final int artificialCount;
    private final int[] artificialRow;
    private final double[] initialBasisDiagonal;

    private final int[] basis;
    private final boolean[] basic;
    private final double[] basicValues;

    private final List<Eta> etas = new ArrayList<>();

    SparseLpSolver(
            int variableCount,
            List<SparseLinearConstraint> constraints,
            double[] objectiveMin
    ) {
        if (variableCount <= 0) throw new IllegalArgumentException("Variable count must be positive");
        if (objectiveMin.length != variableCount) throw new IllegalArgumentException("Objective dimension mismatch");

        this.rowCount = constraints.size();
        this.variableCount = variableCount;
        this.constraints = List.copyOf(constraints);
        this.objectiveMin = objectiveMin.clone();
        this.slackStart = variableCount;

        List<Integer> negativeRows = new ArrayList<>();
        for (int i = 0; i < rowCount; i++) {
            if (constraints.get(i).rhs() < -EPS) negativeRows.add(i);
        }
        this.artificialCount = negativeRows.size();
        this.artificialStart = slackStart + rowCount;
        this.artificialRow = new int[artificialCount];
        for (int i = 0; i < artificialCount; i++) artificialRow[i] = negativeRows.get(i);

        this.initialBasisDiagonal = new double[rowCount];
        this.basis = new int[rowCount];
        this.basicValues = new double[rowCount];
        this.basic = new boolean[variableCount + rowCount + artificialCount];

        Map<Integer, Integer> artificialByRow = new HashMap<>();
        for (int i = 0; i < artificialCount; i++) artificialByRow.put(artificialRow[i], i);

        for (int row = 0; row < rowCount; row++) {
            double rhs = constraints.get(row).rhs();
            Integer artificialIndex = artificialByRow.get(row);
            if (artificialIndex != null) {
                basis[row] = artificialStart + artificialIndex;
                initialBasisDiagonal[row] = -1.0;
                basicValues[row] = -rhs;
            } else {
                basis[row] = slackStart + row;
                initialBasisDiagonal[row] = 1.0;
                basicValues[row] = rhs;
            }
            basic[basis[row]] = true;
        }

        buildColumns();
    }

    Result solve() {
        if (rowCount == 0) {
            double[] solution = new double[variableCount];
            for (double c : objectiveMin) {
                if (c < -EPS) return new Result(false, true, INF, null);
            }
            return new Result(true, false, 0.0, solution);
        }

        // Phase I: minimize the sum of artificial variables.
        if (artificialCount > 0) {
            SimplexResult phaseOne = simplex(true);
            if (!phaseOne.success) return new Result(false, phaseOne.unbounded, INF, null);
            double artificialObjective = phaseOne.objective;
            if (artificialObjective > 1e-6) {
                return new Result(false, false, INF, null);
            }
            cleanBasicValues();
            removeArtificialBasics();
        }

        // Phase II: minimize the actual LP objective. Artificial variables are
        // retained as zero-valued degenerate basis variables if necessary, but
        // are forbidden from entering the phase-II basis.
        SimplexResult phaseTwo = simplex(false);
        if (!phaseTwo.success) {
            return new Result(false, phaseTwo.unbounded, INF, null);
        }

        double[] solution = primalSolution();
        double objective = phaseTwo.objective;
        if (objective < 0 && objective > -1e-7) objective = 0.0;
        return new Result(true, false, objective, solution);
    }

    private SimplexResult simplex(boolean phaseOne) {
        for (int pivot = 0; pivot < MAX_PIVOTS; pivot++) {
            double[] dual = dualVector(phaseOne);

            int entering = chooseEntering(dual, phaseOne);
            if (entering < 0) {
                return new SimplexResult(true, false, objectiveValue(phaseOne));
            }

            double[] direction = direction(entering);
            int leavingRow = chooseLeaving(direction);
            if (leavingRow < 0) {
                // A minimization LP is unbounded below.
                return new SimplexResult(false, true, INF);
            }

            double reducedCost = reducedCostForVariable(entering, dual, phaseOne);
            if (reducedCost >= -EPS) {
                return new SimplexResult(true, false, objectiveValue(phaseOne));
            }

            double pivotElement = direction[leavingRow];
            double theta = basicValues[leavingRow] / pivotElement;
            if (theta < -EPS) {
                throw new IllegalStateException("Negative simplex step");
            }
            if (theta < CLEAN_EPS) theta = 0.0;

            updateBasicValues(direction, leavingRow, theta);

            etas.add(new Eta(leavingRow, direction));

            int leavingVariable = basis[leavingRow];
            basic[leavingVariable] = false;
            basis[leavingRow] = entering;
            basic[entering] = true;
        }
        return new SimplexResult(false, true, INF);
    }

    private int chooseEntering(double[] dual, boolean phaseOne) {
        int candidate = -1;
        double bestReducedCost = -EPS;

        // Original decision variables.
        for (int j = 0; j < variableCount; j++) {
            if (basic[j]) continue;
            double rc = reducedCostForVariable(j, dual, phaseOne);
            if (rc < -EPS
                    && (candidate < 0
                    || rc < bestReducedCost - EPS
                    || (Math.abs(rc - bestReducedCost) <= EPS && j < candidate))) {
                bestReducedCost = rc;
                candidate = j;
            }
        }

        // Slack variables can become nonbasic after pivots.
        for (int row = 0; row < rowCount; row++) {
            int id = slackStart + row;
            if (basic[id]) continue;
            double rc = -dual[row];
            if (rc < -EPS
                    && (candidate < 0
                    || rc < bestReducedCost - EPS
                    || (Math.abs(rc - bestReducedCost) <= EPS && id < candidate))) {
                bestReducedCost = rc;
                candidate = id;
            }
        }

        // Artificial variables participate only in Phase I.
        if (phaseOne) {
            for (int a = 0; a < artificialCount; a++) {
                int id = artificialStart + a;
                if (basic[id]) continue;
                int row = artificialRow[a];
                double rc = 1.0 + dual[row];
                if (rc < -EPS
                        && (candidate < 0
                        || rc < bestReducedCost - EPS
                        || (Math.abs(rc - bestReducedCost) <= EPS && id < candidate))) {
                    bestReducedCost = rc;
                    candidate = id;
                }
            }
        }

        return candidate;
    }

    private double reducedCostForVariable(int variable, double[] dual, boolean phaseOne) {
        if (variable < variableCount) {
            double cost = phaseOne ? 0.0 : objectiveMin[variable];
            int[] rowsForColumn = columnRows[variable];
            double[] valuesForColumn = columnValues[variable];
            for (int k = 0; k < rowsForColumn.length; k++) {
                cost -= valuesForColumn[k] * dual[rowsForColumn[k]];
            }
            return cost;
        }
        if (variable < artificialStart) {
            int row = variable - slackStart;
            return -dual[row];
        }
        int a = variable - artificialStart;
        int row = artificialRow[a];
        return phaseOne ? 1.0 + dual[row] : 0.0;
    }

    private int chooseLeaving(double[] direction) {
        int row = -1;
        double bestRatio = INF;
        int bestBasisId = Integer.MAX_VALUE;

        for (int i = 0; i < rowCount; i++) {
            double d = direction[i];
            if (d <= EPS) continue;
            double ratio = basicValues[i] / d;
            if (ratio < bestRatio - EPS
                    || (Math.abs(ratio - bestRatio) <= EPS
                    && basis[i] < bestBasisId)) {
                bestRatio = ratio;
                bestBasisId = basis[i];
                row = i;
            }
        }
        return row;
    }

    private double[] direction(int variable) {
        double[] vector = new double[rowCount];

        if (variable < variableCount) {
            int[] rowsForColumn = columnRows[variable];
            double[] valuesForColumn = columnValues[variable];
            for (int k = 0; k < rowsForColumn.length; k++) {
                int row = rowsForColumn[k];
                vector[row] = valuesForColumn[k] / initialBasisDiagonal[row];
            }
        } else if (variable < artificialStart) {
            int row = variable - slackStart;
            vector[row] = 1.0 / initialBasisDiagonal[row];
        } else {
            int a = variable - artificialStart;
            int row = artificialRow[a];
            vector[row] = -1.0 / initialBasisDiagonal[row];
        }

        applyInverseForward(vector);
        return vector;
    }

    private void applyInverseForward(double[] vector) {
        for (Eta eta : etas) {
            int r = eta.row;
            double pivot = eta.pivot();
            double z = vector[r] / pivot;
            vector[r] = z;
            double[] d = eta.direction;
            for (int i = 0; i < rowCount; i++) {
                if (i == r) continue;
                double value = d[i];
                if (Math.abs(value) <= CLEAN_EPS) continue;
                vector[i] -= value * z;
            }
        }
    }

    private double[] dualVector(boolean phaseOne) {
        double[] vector = new double[rowCount];

        for (int row = 0; row < rowCount; row++) {
            int variable = basis[row];
            if (phaseOne) {
                vector[row] = isArtificial(variable) ? 1.0 : 0.0;
            } else {
                vector[row] = variable < variableCount ? objectiveMin[variable] : 0.0;
            }
        }

        // B^T y = c_B. For B = B0 E1 ... Ek, solve the eta systems in
        // reverse order, then apply B0^-T (which is diagonal ±1).
        for (int etaIndex = etas.size() - 1; etaIndex >= 0; etaIndex--) {
            Eta eta = etas.get(etaIndex);
            int r = eta.row;
            double[] d = eta.direction;

            double numerator = vector[r];
            for (int i = 0; i < rowCount; i++) {
                if (i == r) continue;
                double value = d[i];
                if (Math.abs(value) <= CLEAN_EPS) continue;
                numerator -= value * vector[i];
            }
            vector[r] = numerator / d[r];
        }

        for (int i = 0; i < rowCount; i++) {
            vector[i] /= initialBasisDiagonal[i];
        }

        return vector;
    }

    private void updateBasicValues(double[] direction, int leavingRow, double theta) {
        for (int i = 0; i < rowCount; i++) {
            double value = basicValues[i] - theta * direction[i];
            if (Math.abs(value) <= CLEAN_EPS) value = 0.0;
            basicValues[i] = value;
        }
        basicValues[leavingRow] = theta;
    }

    /**
     * Artificial variables must not remain as positive-capable basic variables
     * in phase II. At a phase-I optimum their values are zero. Pivot each
     * artificial basic variable out with a non-artificial column while keeping
     * theta = 0. If no such column exists, the row is redundant and the zero
     * artificial basis can safely remain isolated.
     */
    private void removeArtificialBasics() {
        for (int row = 0; row < rowCount; row++) {
            int artificial = basis[row];
            if (!isArtificial(artificial)) continue;
            if (Math.abs(basicValues[row]) > 1e-6) {
                throw new IllegalStateException("Artificial variable remains positive after Phase I");
            }

            int candidate = slackStart + row;
            if (!basic[candidate]) {
                double[] direction = direction(candidate);
                if (Math.abs(direction[row]) > EPS) {
                    pivotAtZero(row, candidate, direction);
                    continue;
                }
            }

            boolean pivoted = false;
            for (int variable = 0; variable < variableCount; variable++) {
                if (basic[variable]) continue;
                double[] direction = direction(variable);
                if (Math.abs(direction[row]) > EPS) {
                    pivotAtZero(row, variable, direction);
                    pivoted = true;
                    break;
                }
            }

            if (pivoted) continue;

            for (int otherRow = 0; otherRow < rowCount; otherRow++) {
                int candidateSlack = slackStart + otherRow;
                if (basic[candidateSlack]) continue;
                double[] direction = direction(candidateSlack);
                if (Math.abs(direction[row]) > EPS) {
                    pivotAtZero(row, candidateSlack, direction);
                    pivoted = true;
                    break;
                }
            }
            // If no column can pivot into this zero row, it is redundant for
            // the remaining model. Keeping the zero artificial basis is safe
            // because no nonbasic non-artificial column can change that row.
        }
    }

    private void pivotAtZero(int leavingRow, int enteringVariable, double[] direction) {
        double pivot = direction[leavingRow];
        if (Math.abs(pivot) <= EPS) throw new IllegalArgumentException("Cannot pivot zero direction");
        etas.add(new Eta(leavingRow, direction));
        int leavingVariable = basis[leavingRow];
        basic[leavingVariable] = false;
        basis[leavingRow] = enteringVariable;
        basic[enteringVariable] = true;
        basicValues[leavingRow] = 0.0;
    }

    private double objectiveValue(boolean phaseOne) {
        double value = 0.0;
        for (int row = 0; row < rowCount; row++) {
            int variable = basis[row];
            double cost = phaseOne
                    ? (isArtificial(variable) ? 1.0 : 0.0)
                    : (variable < variableCount ? objectiveMin[variable] : 0.0);
            value += cost * basicValues[row];
        }
        return value;
    }

    private boolean isArtificial(int variable) {
        return variable >= artificialStart;
    }

    private double[] primalSolution() {
        double[] x = new double[variableCount];
        for (int row = 0; row < rowCount; row++) {
            int variable = basis[row];
            if (variable >= 0 && variable < variableCount) {
                x[variable] = clean(basicValues[row]);
            }
        }
        return x;
    }

    private void cleanBasicValues() {
        for (int i = 0; i < basicValues.length; i++) {
            if (Math.abs(basicValues[i]) <= 1e-7) basicValues[i] = 0.0;
            if (basicValues[i] < -EPS) {
                throw new IllegalStateException("Phase-I produced an infeasible basic solution");
            }
        }
    }

    private static double clean(double value) {
        return Math.abs(value) <= 1e-8 ? 0.0 : value;
    }

    private void buildColumns() {
        int candidateCount = variableCount + rowCount + artificialCount;
        int[] counts = new int[variableCount];
        for (SparseLinearConstraint constraint : constraints) {
            for (int p = 0; p < constraint.size(); p++) {
                int index = constraint.indexAt(p);
                if (index < variableCount) counts[index]++;
            }
        }

        this.columnRows = new int[variableCount][];
        this.columnValues = new double[variableCount][];
        for (int j = 0; j < variableCount; j++) {
            columnRows[j] = new int[counts[j]];
            columnValues[j] = new double[counts[j]];
        }

        int[] offsets = new int[variableCount];
        for (int row = 0; row < constraints.size(); row++) {
            SparseLinearConstraint constraint = constraints.get(row);
            for (int p = 0; p < constraint.size(); p++) {
                int index = constraint.indexAt(p);
                if (index >= variableCount) continue;
                int offset = offsets[index]++;
                columnRows[index][offset] = row;
                columnValues[index][offset] = constraint.coefficientAt(p);
            }
        }
    }

    private record Eta(int row, double[] direction) {
        private double pivot() {
            return direction[row];
        }
    }

    private record SimplexResult(boolean success, boolean unbounded, double objective) {}

    static final class Result {
        private final boolean feasible;
        private final boolean unbounded;
        private final double objective;
        private final double[] solution;

        Result(boolean feasible, boolean unbounded, double objective, double[] solution) {
            this.feasible = feasible;
            this.unbounded = unbounded;
            this.objective = objective;
            this.solution = solution;
        }

        boolean feasible() { return feasible; }
        boolean unbounded() { return unbounded; }
        double objective() { return objective; }
        double[] solution() { return solution; }
    }
}
