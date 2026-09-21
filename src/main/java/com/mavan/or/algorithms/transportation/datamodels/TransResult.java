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
                          double initialCost, List<InitialSolutionStep> initialSteps,
                          List<TransState> states, double[][] optimalAllocation,
                          boolean[][] optimalBasic, double[] optimalU, double[] optimalV, double optimalCost,
                          double[][] costs, double[] supply, double[] demand,
                          String[] sourceLabels, String[] destinationLabels,
                          boolean dummyAdded, String dummyType, int dummyIndex, double dummyQuantity) {
    public TransResult(ETransBasicSolution initialMethod, double[][] initialAllocation, boolean[][] initialBasic,
                       double initialCost, List<TransState> states, double[][] optimalAllocation,
                       boolean[][] optimalBasic, double[] optimalU, double[] optimalV, double optimalCost) {
        this(initialMethod, initialAllocation, initialBasic, initialCost, List.of(), states,
                optimalAllocation, optimalBasic, optimalU, optimalV, optimalCost,
                new double[initialAllocation.length][initialAllocation[0].length],
                new double[initialAllocation.length], new double[initialAllocation[0].length],
                defaultLabels("S", initialAllocation.length), defaultLabels("D", initialAllocation[0].length),
                false, "NONE", -1, 0.0);
    }

    public TransResult {
        initialAllocation = Util.deepCopy(initialAllocation);
        initialBasic = Util.deepCopy(initialBasic);
        initialSteps = List.copyOf(initialSteps);
        states = Collections.unmodifiableList(new ArrayList<>(states));
        optimalAllocation = Util.deepCopy(optimalAllocation);
        optimalBasic = Util.deepCopy(optimalBasic);
        optimalU = optimalU.clone();
        optimalV = optimalV.clone();
        costs = Util.deepCopy(costs);
        supply = supply.clone();
        demand = demand.clone();
        sourceLabels = sourceLabels.clone();
        destinationLabels = destinationLabels.clone();
    }

    @Override
    public double[][] initialAllocation() {
        return Util.deepCopy(initialAllocation);
    }

    @Override
    public boolean[][] initialBasic() {
        return Util.deepCopy(initialBasic);
    }

    @Override
    public double[][] optimalAllocation() {
        return Util.deepCopy(optimalAllocation);
    }

    @Override
    public boolean[][] optimalBasic() {
        return Util.deepCopy(optimalBasic);
    }

    @Override
    public double[] optimalU() {
        return optimalU.clone();
    }

    @Override
    public double[] optimalV() {
        return optimalV.clone();
    }

    @Override public double[][] costs() { return Util.deepCopy(costs); }
    @Override public double[] supply() { return supply.clone(); }
    @Override public double[] demand() { return demand.clone(); }
    @Override public String[] sourceLabels() { return sourceLabels.clone(); }
    @Override public String[] destinationLabels() { return destinationLabels.clone(); }

    private static String[] defaultLabels(String prefix, int length) {
        String[] labels = new String[length];
        for (int i = 0; i < length; i++) labels[i] = prefix + (i + 1);
        return labels;
    }

}
