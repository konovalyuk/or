package com.mavan.or.newpost.optimization;

import java.util.*;

/** Internal sparse MILP model. */
final class SparseMilpModel {
    final int variableCount;
    final double[] objectiveMin;
    final boolean[] integer;
    final double[] upperBounds;
    final List<SparseLinearConstraint> constraints;

    SparseMilpModel(int variableCount) {
        if (variableCount <= 0) throw new IllegalArgumentException("Variable count must be positive");
        this.variableCount = variableCount;
        this.objectiveMin = new double[variableCount];
        this.integer = new boolean[variableCount];
        this.upperBounds = new double[variableCount];
        Arrays.fill(this.upperBounds, Double.POSITIVE_INFINITY);
        this.constraints = new ArrayList<>();
    }
}
