package com.mavan.or.algorithms.transportation.start;

import com.mavan.or.algorithms.transportation.datamodels.TransTable;
import com.mavan.or.algorithms.transportation.datamodels.TransTableCell;
import com.mavan.or.algorithms.transportation.Util;
import org.springframework.stereotype.Service;

@Service
public class LeastCost {

    /**
     * 2. Least-cost method.
     * <p>
     * Repeatedly choose the currently available cell with minimum cost.
     */
    public TransTable buildLeastCostSolution(TransTable table) {
        double[] remainingSupply = table.supply().clone();
        double[] remainingDemand = table.demand().clone();

        boolean[] rowClosed = new boolean[table.getRows()];
        boolean[] columnClosed = new boolean[table.getColumns()];

        int basicCount = 0;
        int requiredBasics = table.getRows() + table.getColumns() - 1;

        while (basicCount < requiredBasics) {
            TransTableCell best = findLeastCostAvailableCell(
                    remainingSupply,
                    remainingDemand,
                    rowClosed,
                    columnClosed,
                    table
            );

            if (best == null) {
                break;
            }

            double quantity = Math.min(remainingSupply[best.row()], remainingDemand[best.column()]);

            table.allocation()[best.row()][best.column()] = quantity;
            table.basic()[best.row()][best.column()] = true;
            basicCount++;

            remainingSupply[best.row()] -= quantity;
            remainingDemand[best.column()] -= quantity;

            boolean rowSatisfied = Math.abs(remainingSupply[best.row()]) <= TransTable.EPS;

            boolean columnSatisfied = Math.abs(remainingDemand[best.column()]) <= TransTable.EPS;

            if (rowSatisfied) {
                rowClosed[best.row()] = true;
            }

            if (columnSatisfied) {
                columnClosed[best.column()] = true;
            }

            /*
             * If both become zero simultaneously, the resulting solution
             * can be degenerate. repairDegeneracy() handles the required
             * zero basic variable(s).
             */
        }
        return table;
    }

    private TransTableCell findLeastCostAvailableCell(
            double[] remainingSupply,
            double[] remainingDemand,
            boolean[] rowClosed,
            boolean[] columnClosed,
            TransTable table
    ) {
        TransTableCell best = null;

        for (int i = 0; i < table.getRows(); i++) {
            if (rowClosed[i] || remainingSupply[i] <= TransTable.EPS) {
                continue;
            }

            for (int j = 0; j < table.getColumns(); j++) {
                if (columnClosed[j] || remainingDemand[j] <= TransTable.EPS) {
                    continue;
                }

                var candidate = new TransTableCell(i, j);
                if (best == null || table.costs()[i][j] < table.costs()[best.row()][best.column()] - TransTable.EPS
                        || (Math.abs(table.costs()[i][j] - table.costs()[best.row()][best.column()]) <= TransTable.EPS
                        && Util.compareCells(candidate, best) < 0
                )) {
                    best = candidate;
                }
            }
        }

        return best;
    }

}
