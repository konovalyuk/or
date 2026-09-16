package com.mavan.or.algorithms.transportation.start;

import com.mavan.or.algorithms.transportation.datamodels.TransTable;
import org.springframework.stereotype.Service;

@Service
public class NorthwestCorner {

    /**
     * 1. Northwest-corner method.
     * <p>
     * Start from the northwest cell and satisfy the current row/column
     * as much as possible.
     */
    public TransTable buildNorthwestCornerSolution(TransTable table) {
        double[] remainingSupply = table.supply().clone();
        double[] remainingDemand = table.demand().clone();

        int i = 0;
        int j = 0;
        while (i < table.getRows() && j < table.getColumns()) {
            double quantity = Math.min(remainingSupply[i], remainingDemand[j]);

            table.allocation()[i][j] = quantity;
            table.basic()[i][j] = true;

            remainingSupply[i] -= quantity;
            remainingDemand[j] -= quantity;

            boolean rowSatisfied = Math.abs(remainingSupply[i]) <= TransTable.EPS;
            boolean columnSatisfied = Math.abs(remainingDemand[j]) <= TransTable.EPS;

            if (rowSatisfied && columnSatisfied) {
                i++;
                j++;
            } else if (rowSatisfied) {
                i++;
            } else if (columnSatisfied) {
                j++;
            }
        }
        return table;
    }

}
