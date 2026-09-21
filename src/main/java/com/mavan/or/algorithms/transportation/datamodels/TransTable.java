package com.mavan.or.algorithms.transportation.datamodels;

import com.mavan.or.algorithms.transportation.TransValidation;
import com.mavan.or.algorithms.transportation.Util;

/**
 * @param allocation Current basic solution.
 * @param basic      True => cell belongs to the basis.
 *                   <p>
 *                   Important:
 *                   a basic cell may contain zero in a degenerate transportation solution.
 */
public record TransTable(double[][] costs, double[] supply, double[] demand, double[][] allocation, boolean[][] basic) {

    public static final double EPS = 1.0e-9;

    public TransTable(double[][] costs, double[] supply, double[] demand, double[][] allocation, boolean[][] basic) {
        TransValidation.validateInput(costs, supply, demand);

        this.costs = Util.deepCopy(costs);
        this.supply = supply.clone();
        this.demand = demand.clone();

        TransValidation.validateBalancedProblem(supply, demand);

        TransValidation.validateWorkingState(allocation, basic, costs.length, costs[0].length);

        this.allocation = Util.deepCopy(allocation);
        this.basic = Util.deepCopy(basic);
    }

    public int getRows() {
        return costs.length;
    }

    public int getColumns() {
        return costs[0].length;
    }

    public double costAt(int row, int column) {
        return costs[row][column];
    }

    public double supplyAt(int row) {
        return supply[row];
    }

    public double demandAt(int column) {
        return demand[column];
    }

}
