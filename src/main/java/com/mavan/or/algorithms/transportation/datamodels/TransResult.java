package com.mavan.or.algorithms.transportation.datamodels;

import com.mavan.or.algorithms.transportation.start.ETransBasicSolution;
import com.mavan.or.algorithms.transportation.Util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Final result.
 */
public record TransResult(ETransBasicSolution initialMethod, double[][] initialAllocation, boolean[][] initialBasic,
                          double initialCost, List<TransState> states, double[][] optimalAllocation,
                          boolean[][] optimalBasic, double[] optimalU, double[] optimalV, double optimalCost) {
    public TransResult(ETransBasicSolution initialMethod, double[][] initialAllocation, boolean[][] initialBasic, double initialCost, List<TransState> states, double[][] optimalAllocation, boolean[][] optimalBasic, double[] optimalU, double[] optimalV, double optimalCost) {
        this.initialMethod = initialMethod;
        this.initialAllocation = Util.deepCopy(initialAllocation);
        this.initialBasic = Util.deepCopy(initialBasic);
        this.initialCost = initialCost;
        this.states = Collections.unmodifiableList(new ArrayList<>(states));
        this.optimalAllocation = Util.deepCopy(optimalAllocation);
        this.optimalBasic = Util.deepCopy(optimalBasic);
        this.optimalU = optimalU.clone();
        this.optimalV = optimalV.clone();
        this.optimalCost = optimalCost;
    }

}
