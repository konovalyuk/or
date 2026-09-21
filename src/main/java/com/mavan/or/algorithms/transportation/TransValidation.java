package com.mavan.or.algorithms.transportation;

import com.mavan.or.algorithms.transportation.datamodels.TransTable;

import java.util.Locale;

public final class TransValidation {

    private TransValidation() {
    }

    public static void validateInput(double[][] costs, double[] supply, double[] demand) {
        if (costs == null || costs.length == 0) {
            throw new IllegalArgumentException("At least one source is required.");
        }
        if (costs[0] == null || costs[0].length == 0) {
            throw new IllegalArgumentException("At least one destination is required.");
        }

        int columns = costs[0].length;
        for (double[] row : costs) {
            if (row == null || row.length != columns) {
                throw new IllegalArgumentException("Cost matrix must be rectangular.");
            }
            for (double cost : row) {
                if (!Double.isFinite(cost)) {
                    throw new IllegalArgumentException("All transportation costs must be finite.");
                }
                if (cost < 0.0) {
                    throw new IllegalArgumentException("Transportation costs cannot be negative.");
                }
            }
        }

        if (supply == null || supply.length != costs.length) {
            throw new IllegalArgumentException("Supply vector dimension does not match the number of rows.");
        }
        if (demand == null || demand.length != columns) {
            throw new IllegalArgumentException("Demand vector dimension does not match the number of columns.");
        }

        for (double value : supply) {
            if (!Double.isFinite(value) || value < 0.0) {
                throw new IllegalArgumentException("Supply values must be finite and nonnegative.");
            }
        }

        for (double value : demand) {
            if (!Double.isFinite(value) || value < 0.0) {
                throw new IllegalArgumentException("Demands values must be finite and nonnegative.");
            }
        }
    }

    public static void validateBalancedProblem(double[] supply, double[] demand) {
        double totalSupply = Util.sum(supply);
        double totalDemand = Util.sum(demand);

        if (Math.abs(totalSupply - totalDemand) > TransTable.EPS) {
            throw new IllegalArgumentException(String.format(
                    Locale.US,
                    "Transportation problem is unbalanced. "
                            + "Total supply = %.10f, total demand = %.10f. "
                            + "Balance the problem before applying this implementation.",
                    totalSupply,
                    totalDemand));
        }
    }

    public static void validateWorkingState(double[][] allocation, boolean[][] basic,
                                            int rows, int columns) {
        if (allocation == null || allocation.length != rows) {
            throw new IllegalArgumentException("Allocation matrix row count does not match costs.");
        }
        if (basic == null || basic.length != rows) {
            throw new IllegalArgumentException("Basis matrix row count does not match costs.");
        }
        for (int i = 0; i < rows; i++) {
            if (allocation[i] == null || allocation[i].length != columns) {
                throw new IllegalArgumentException("Allocation matrix must be rectangular.");
            }
            if (basic[i] == null || basic[i].length != columns) {
                throw new IllegalArgumentException("Basis matrix must be rectangular.");
            }
            for (double value : allocation[i]) {
                if (!Double.isFinite(value) || value < 0.0) {
                    throw new IllegalArgumentException("Allocations must be finite and nonnegative.");
                }
            }
        }
    }

    private static void validateSolutionDimensions(double[][] solution, TransTable table) {
        if (solution == null || solution.length != table.getRows()) {
            throw new IllegalArgumentException("Invalid solution matrix row count.");
        }

        for (double[] row : solution) {
            if (row == null || row.length != table.getColumns()) {
                throw new IllegalArgumentException("Invalid solution matrix column count.");
            }
        }
    }

    public static void validateSolution(double[][] solution, TransTable table) {
        validateSolutionDimensions(solution, table);

        for (int i = 0; i < table.getRows(); i++) {
            double rowSum = 0.0;

            for (int j = 0; j < table.getColumns(); j++) {
                if (solution[i][j] < -TransTable.EPS) {
                    throw new IllegalArgumentException("Negative allocation at x" + (i + 1) + (j + 1));
                }
                rowSum += solution[i][j];
            }

            if (Math.abs(rowSum - table.supplyAt(i)) > TransTable.EPS) {
                throw new IllegalArgumentException("Supply constraint violated for row " + (i + 1) + ": expected " + table.supplyAt(i) + ", got " + rowSum);
            }
        }

        for (int j = 0; j < table.getColumns(); j++) {
            double columnSum = 0.0;

            for (int i = 0; i < table.getRows(); i++) {
                columnSum += solution[i][j];
            }

            if (Math.abs(columnSum - table.demandAt(j)) > TransTable.EPS) {
                throw new IllegalArgumentException("Demand constraint violated for column " + (j + 1) + ": expected " + table.demandAt(j) + ", got " + columnSum);
            }
        }
    }

}
