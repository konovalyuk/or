package com.mavan.or.algorithms.transportation.datamodels;

import com.mavan.or.algorithms.transportation.Util;

/** Immutable browser-friendly snapshot of one initial-solution allocation. */
public record InitialSolutionStep(int step, int selectedRow, int selectedColumn,
                                  double allocatedAmount, double[][] allocation,
                                  boolean[][] basic, double[] remainingSupply,
                                  double[] remainingDemand, double totalCost) {
    public InitialSolutionStep {
        allocation = Util.deepCopy(allocation);
        basic = Util.deepCopy(basic);
        remainingSupply = remainingSupply.clone();
        remainingDemand = remainingDemand.clone();
    }

    @Override public double[][] allocation() { return Util.deepCopy(allocation); }
    @Override public boolean[][] basic() { return Util.deepCopy(basic); }
    @Override public double[] remainingSupply() { return remainingSupply.clone(); }
    @Override public double[] remainingDemand() { return remainingDemand.clone(); }
}
