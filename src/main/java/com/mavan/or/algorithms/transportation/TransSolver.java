package com.mavan.or.algorithms.transportation;

import com.mavan.or.algorithms.transportation.datamodels.TransResult;
import com.mavan.or.algorithms.transportation.datamodels.TransState;
import com.mavan.or.algorithms.transportation.datamodels.TransTable;
import com.mavan.or.algorithms.transportation.datamodels.TransTableCell;
import com.mavan.or.algorithms.transportation.start.ETransBasicSolution;
import com.mavan.or.algorithms.transportation.start.TransBasicSolution;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Transportation Problem
 * <p>
 * Implementation of the transportation algorithm described by
 * Hamdy A. Taha, Operations Research: An Introduction, 10th ed.,
 * Chapter 5, Transportation Model and Its Variants, Section 5.3.
 * <p>
 * Supported starting basic feasible solutions:
 * 1. Northwest-corner method
 * 2. Least-cost method
 * 3. Vogel approximation method
 * <p>
 * Iterative transportation simplex:
 * Step 1. Build a starting basic feasible solution.
 * Step 2. Compute potentials u_i and v_j and evaluate
 * R_ij = u_i + v_j - c_ij.
 * If all nonbasic R_ij <= 0, the current solution is optimal.
 * Step 3. Select an entering variable with R_ij > 0.
 * Step 4. Build the closed loop through basic cells.
 * Step 5. Determine theta from cells marked with '-'.
 * Step 6. Change the basis and repeat.
 * <p>
 * The class is deliberately separated into:
 * - solver / algorithm
 * - immutable iteration/result data
 * - console presentation
 * <p>
 * This makes it straightforward to replace the console renderer
 * with an HTTP/REST/browser renderer later.
 * <p>
 * Tested with the SunRay Transport example:
 * <p>
 * Mill 1  Mill 2  Mill 3  Mill 4   Supply
 * Silo 1      10      2      20      11        15
 * Silo 2      12      7       9      20        25
 * Silo 3       4     14      16      18        10
 * Demand       5     15      15      15
 * <p>
 * Optimal transportation cost = 435
 * (hundreds of dollars in the Taha example).
 */
@Service
public class TransSolver {

    private final TransBasicSolution basicSolution;
    private final TransClosedLoop closedLoop;
    private final TransPotentials potentials;

    public TransSolver(TransBasicSolution basicSolution, TransClosedLoop closedLoop, TransPotentials potentials) {
        this.basicSolution = basicSolution;
        this.closedLoop = closedLoop;
        this.potentials = potentials;
    }

    public TransResult solve(ETransBasicSolution method, TransTable table) {
        return solve(method, null, table);
    }

    public TransResult solve(ETransBasicSolution method, IterationObserver observer, TransTable table) {
        return solve(method, observer, table, defaultLabels("S", table.getRows()),
                defaultLabels("D", table.getColumns()), false, "NONE", -1, 0.0);
    }

    public TransResult solve(ETransBasicSolution method, IterationObserver observer, TransTable table,
                             String[] sourceLabels, String[] destinationLabels,
                             boolean dummyAdded, String dummyType, int dummyIndex, double dummyQuantity) {
        Objects.requireNonNull(method, "Basic solution method must not be null.");

        List<com.mavan.or.algorithms.transportation.datamodels.InitialSolutionStep> initialSteps = new ArrayList<>();
        basicSolution.buildInitialBasicSolution(method, table, (row, column, amount, current) ->
                initialSteps.add(new com.mavan.or.algorithms.transportation.datamodels.InitialSolutionStep(
                        initialSteps.size() + 1, row, column, amount,
                        current.allocation(), current.basic(), remainingSupply(current),
                        remainingDemand(current), calculateTotalCost(current))));
        repairDegeneracy(table);

        double[][] initialAllocation = Util.deepCopy(table.allocation());
        boolean[][] initialBasic = Util.deepCopy(table.basic());
        double initialCost = calculateTotalCost(table);
        if (initialSteps.isEmpty() || !sameAllocation(initialSteps.get(initialSteps.size() - 1).allocation(), table.allocation())
                || !sameBasic(initialSteps.get(initialSteps.size() - 1).basic(), table.basic())) {
            initialSteps.add(new com.mavan.or.algorithms.transportation.datamodels.InitialSolutionStep(
                    initialSteps.size() + 1, -1, -1, 0.0, table.allocation(), table.basic(),
                    remainingSupply(table), remainingDemand(table), initialCost));
        }

        List<TransState> states = new ArrayList<>();

        int iteration = 0;

        while (true) {
            double[] u = new double[table.getRows()];
            double[] v = new double[table.getColumns()];

            potentials.calculatePotentials(u, v, table);

            double[][] evaluation = new double[table.getRows()][table.getColumns()];

            TransTableCell entering = potentials.findEnteringVariable(u, v, evaluation, table);
            if (entering == null) {
                var snapshot = Util.deepCopy(table.allocation());
                var basicSnapshot = Util.deepCopy(table.basic());
                var optimalState = new TransState(iteration, snapshot, basicSnapshot, u, v, evaluation,
                        null, null, Collections.emptyList(), 0.0,
                        snapshot, basicSnapshot, calculateTotalCost(table), true);
                states.add(optimalState);

                if (observer != null) {
                    observer.onState(optimalState);
                }

                return new TransResult(method, initialAllocation, initialBasic, initialCost, initialSteps, states,
                        table.allocation(), table.basic(), u, v, calculateTotalCost(table), table.costs(),
                        table.supply(), table.demand(), sourceLabels, destinationLabels,
                        dummyAdded, dummyType, dummyIndex, dummyQuantity);
            }

            double[][] allocationBefore = Util.deepCopy(table.allocation());
            boolean[][] basicBefore = Util.deepCopy(table.basic());

            /*
             * Construct the closed loop:
             *
             * + entering
             * - basic
             * + basic
             * - basic
             * ...
             *
             * The last cell in the returned list is not a duplicate
             * of the entering cell.
             */
            List<TransTableCell> cycle = closedLoop.findClosedLoop(entering, table);

            if (cycle == null || cycle.size() < 4) {
                throw new IllegalStateException("Unable to construct a valid transportation cycle for " + entering);
            }

            /*
             * Feasibility condition:
             *
             * theta = min{x_ij | cell has '-'}
             *
             * A cell attaining theta becomes the leaving variable.
             */
            double theta = Double.POSITIVE_INFINITY;
            TransTableCell leaving = null;

            for (int index = 1; index < cycle.size(); index += 2) {
                TransTableCell cell = cycle.get(index);
                double value = table.allocation()[cell.row()][cell.column()];

                if (value < theta - TransTable.EPS) {
                    theta = value;
                    leaving = cell;
                } else if (Math.abs(value - theta) <= TransTable.EPS
                        && leaving != null
                        && Util.compareCells(cell, leaving) < 0) {
                    leaving = cell;
                }
            }

            if (leaving == null || Double.isInfinite(theta)) {
                throw new IllegalStateException("Unable to determine the leaving variable.");
            }

            /*
             * Pivot:
             *
             * + cells receive theta
             * - cells lose theta
             */
            for (int index = 0; index < cycle.size(); index++) {
                TransTableCell cell = cycle.get(index);

                if (index % 2 == 0) {
                    table.allocation()[cell.row()][cell.column()] += theta;
                } else {
                    table.allocation()[cell.row()][cell.column()] -= theta;
                }

                if (Math.abs(table.allocation()[cell.row()][cell.column()]) <= TransTable.EPS) {
                    table.allocation()[cell.row()][cell.column()] = 0.0;
                }
            }

            /*
             * Change basis.
             */
            table.basic()[entering.row()][entering.column()] = true;
            table.basic()[leaving.row()][leaving.column()] = false;

            /*
             * Degeneracy:
             * after a pivot we may have fewer than m+n-1 basic variables.
             * Add independent zero basic variables where necessary.
             */
            repairDegeneracy(table);

            double newCost = calculateTotalCost(table);

            var state = new TransState(++iteration, allocationBefore, basicBefore, u, v, evaluation,
                    entering, leaving, cycle, theta,
                    table.allocation(), table.basic(), newCost, false);

            states.add(state);

            if (observer != null) {
                observer.onState(state);
            }
        }
    }

    private double[] remainingSupply(TransTable table) {
        double[] remaining = table.supply().clone();
        for (int i = 0; i < table.getRows(); i++) {
            for (int j = 0; j < table.getColumns(); j++) remaining[i] -= table.allocation()[i][j];
        }
        return remaining;
    }

    private double[] remainingDemand(TransTable table) {
        double[] remaining = table.demand().clone();
        for (int j = 0; j < table.getColumns(); j++) {
            for (int i = 0; i < table.getRows(); i++) remaining[j] -= table.allocation()[i][j];
        }
        return remaining;
    }

    private boolean sameAllocation(double[][] left, double[][] right) {
        return Arrays.deepEquals(left, right);
    }

    private boolean sameBasic(boolean[][] left, boolean[][] right) {
        return Arrays.deepEquals(left, right);
    }

    private String[] defaultLabels(String prefix, int length) {
        String[] labels = new String[length];
        for (int i = 0; i < length; i++) labels[i] = prefix + (i + 1);
        return labels;
    }

    /**
     * A nondegenerate transportation basis contains exactly:
     * <p>
     * rows + columns - 1
     * <p>
     * basic variables.
     * <p>
     * When supply and demand are simultaneously exhausted during an
     * allocation, fewer positive basic variables can result.
     * <p>
     * We therefore add zero-valued independent basic cells.
     * <p>
     * A candidate is accepted only if adding it does NOT create a cycle.
     * Thus the basis remains a spanning tree.
     */
    public void repairDegeneracy(TransTable table) {
        int required = table.getRows() + table.getColumns() - 1;
        int current = Util.countBasicVariables(table);

        if (current > required) {
            throw new IllegalStateException("Invalid transportation basis: " + current + " basic variables, expected at most " + required);
        }

        while (current < required) {
            TransTableCell candidate = null;
            for (int i = 0; i < table.getRows(); i++) {
                for (int j = 0; j < table.getColumns(); j++) {
                    if (table.basic()[i][j]) {
                        continue;
                    }
                    if (createsCycleIfAdded(i, j, table)) {
                        continue;
                    }

                    var currentCandidate = new TransTableCell(i, j);
                    if (candidate == null || Util.compareCells(currentCandidate, candidate) < 0) {
                        candidate = currentCandidate;
                    }
                }
            }
            if (candidate == null) {
                throw new IllegalStateException("Unable to repair a degenerate transportation basis.");
            }

            table.basic()[candidate.row()][candidate.column()] = true;
            table.allocation()[candidate.row()][candidate.column()] = 0.0;

            current++;
        }
    }

    private boolean createsCycleIfAdded(int row, int column, TransTable table) {
        table.basic()[row][column] = true;
        try {
            List<TransTableCell> cycle = closedLoop.findClosedLoop(new TransTableCell(row, column), table);
            return cycle != null;
        } finally {
            table.basic()[row][column] = false;
        }
    }

    public double calculateTotalCost(TransTable table) {
        double result = 0.0;

        for (int i = 0; i < table.getRows(); i++) {
            for (int j = 0; j < table.getColumns(); j++) {
                result += table.allocation()[i][j] * table.costAt(i, j);
            }
        }

        return result;
    }

}
