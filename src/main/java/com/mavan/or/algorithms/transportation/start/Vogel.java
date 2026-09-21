package com.mavan.or.algorithms.transportation.start;

import com.mavan.or.algorithms.transportation.datamodels.TransTable;
import com.mavan.or.algorithms.transportation.datamodels.TransTableCell;
import com.mavan.or.algorithms.transportation.Util;
import org.springframework.stereotype.Service;

import java.util.Arrays;

@Service
public class Vogel {

    /**
     * 5.3.1 Vogel approximation method.
     * <p>
     * For every active row/column:
     * <p>
     * penalty = second-smallest cost - smallest cost
     * <p>
     * Select the row/column with the greatest penalty, and then allocate
     * as much as possible to its least-cost cell.
     */
    public void buildVogelSolution(TransTable table) {
        buildVogelSolution(table, null);
    }

    public void buildVogelSolution(TransTable table, InitialStepObserver observer) {
        double[] remainingSupply = table.supply().clone();
        double[] remainingDemand = table.demand().clone();

        boolean[] rowClosed = new boolean[table.getRows()];
        boolean[] columnClosed = new boolean[table.getColumns()];

        int remainingRows = table.getRows();
        int remainingColumns = table.getColumns();

        while (remainingRows > 0 && remainingColumns > 0) {
            double[] rowPenalty = calculateRowPenalties(rowClosed, columnClosed, table);

            double[] columnPenalty = calculateColumnPenalties(rowClosed, columnClosed, table);

            boolean chooseRow = true;
            int selectedIndex = -1;
            double bestPenalty = -1.0;

            /*
             * Deterministic tie-breaking:
             * first scan rows, then columns.
             */
            for (int i = 0; i < table.getRows(); i++) {
                if (rowClosed[i]) {
                    continue;
                }

                if (rowPenalty[i] > bestPenalty + TransTable.EPS
                        || (Math.abs(rowPenalty[i] - bestPenalty) <= TransTable.EPS
                        && (!chooseRow || selectedIndex < 0 || i < selectedIndex)
                )) {
                    bestPenalty = rowPenalty[i];
                    selectedIndex = i;
                    chooseRow = true;
                }
            }

            for (int j = 0; j < table.getColumns(); j++) {
                if (columnClosed[j]) {
                    continue;
                }

                if (columnPenalty[j] > bestPenalty + TransTable.EPS
                        || (Math.abs(columnPenalty[j] - bestPenalty) <= TransTable.EPS
                        && (!chooseRow || selectedIndex < 0 || j < selectedIndex)
                )) {
                    bestPenalty = columnPenalty[j];
                    selectedIndex = j;
                    chooseRow = false;
                }
            }

            TransTableCell selectedCell;

            if (chooseRow) {
                selectedCell = findLeastCostCellInRow(selectedIndex, columnClosed, table);
            } else {
                selectedCell = findLeastCostCellInColumn(selectedIndex, rowClosed, table);
            }

            if (selectedCell == null) {
                throw new IllegalStateException("Vogel approximation failed to find an available cell.");
            }

            double quantity = Math.min(
                    remainingSupply[selectedCell.row()],
                    remainingDemand[selectedCell.column()]
            );

            table.allocation()[selectedCell.row()][selectedCell.column()] = quantity;
            table.basic()[selectedCell.row()][selectedCell.column()] = true;

            remainingSupply[selectedCell.row()] -= quantity;
            remainingDemand[selectedCell.column()] -= quantity;
            if (observer != null) observer.onStep(selectedCell.row(), selectedCell.column(), quantity, table);

            boolean rowSatisfied = Math.abs(remainingSupply[selectedCell.row()]) <= TransTable.EPS;

            boolean columnSatisfied = Math.abs(remainingDemand[selectedCell.column()]) <= TransTable.EPS;

            if (rowSatisfied && !rowClosed[selectedCell.row()]) {
                rowClosed[selectedCell.row()] = true;
                remainingRows--;
            }

            if (columnSatisfied && !columnClosed[selectedCell.column()]) {
                columnClosed[selectedCell.column()] = true;
                remainingColumns--;
            }
        }
    }


    private double[] calculateRowPenalties(boolean[] rowClosed, boolean[] columnClosed, TransTable table) {
        double[] penalties = new double[table.getRows()];

        Arrays.fill(penalties, -1.0);

        for (int i = 0; i < table.getRows(); i++) {
            if (rowClosed[i]) {
                continue;
            }

            double smallest = Double.POSITIVE_INFINITY;
            double secondSmallest = Double.POSITIVE_INFINITY;

            for (int j = 0; j < table.getColumns(); j++) {
                if (columnClosed[j]) {
                    continue;
                }

                double value = table.costAt(i, j);

                if (value < smallest) {
                    secondSmallest = smallest;
                    smallest = value;
                } else if (value < secondSmallest) {
                    secondSmallest = value;
                }
            }

            if (secondSmallest == Double.POSITIVE_INFINITY) {
                penalties[i] = smallest;
            } else {
                penalties[i] = secondSmallest - smallest;
            }
        }

        return penalties;
    }

    private double[] calculateColumnPenalties(boolean[] rowClosed, boolean[] columnClosed, TransTable table) {
        double[] penalties = new double[table.getColumns()];

        Arrays.fill(penalties, -1.0);

        for (int j = 0; j < table.getColumns(); j++) {
            if (columnClosed[j]) {
                continue;
            }

            double smallest = Double.POSITIVE_INFINITY;
            double secondSmallest = Double.POSITIVE_INFINITY;

            for (int i = 0; i < table.getRows(); i++) {
                if (rowClosed[i]) {
                    continue;
                }

                double value = table.costAt(i, j);

                if (value < smallest) {
                    secondSmallest = smallest;
                    smallest = value;
                } else if (value < secondSmallest) {
                    secondSmallest = value;
                }
            }

            if (secondSmallest == Double.POSITIVE_INFINITY) {
                penalties[j] = smallest;
            } else {
                penalties[j] = secondSmallest - smallest;
            }
        }

        return penalties;
    }

    private TransTableCell findLeastCostCellInRow(int row, boolean[] columnClosed, TransTable table) {
        TransTableCell best = null;

        for (int j = 0; j < table.getColumns(); j++) {
            if (columnClosed[j]) {
                continue;
            }

            TransTableCell candidate = new TransTableCell(row, j);

            if (best == null
                    || table.costAt(row, j) < table.costAt(best.row(), best.column()) - TransTable.EPS
                    || (Math.abs(table.costAt(row, j) - table.costAt(best.row(), best.column())) <= TransTable.EPS
                    && Util.compareCells(candidate, best) < 0)) {
                best = candidate;
            }
        }

        return best;
    }

    private TransTableCell findLeastCostCellInColumn(int column, boolean[] rowClosed, TransTable table) {
        TransTableCell best = null;

        for (int i = 0; i < table.getRows(); i++) {
            if (rowClosed[i]) {
                continue;
            }

            TransTableCell candidate = new TransTableCell(i, column);

            if (best == null
                    || table.costAt(i, column) < table.costAt(best.row(), best.column()) - TransTable.EPS
                    || (Math.abs(table.costAt(i, column) - table.costAt(best.row(), best.column())) <= TransTable.EPS
                    && Util.compareCells(candidate, best) < 0)) {
                best = candidate;
            }
        }

        return best;
    }

}
