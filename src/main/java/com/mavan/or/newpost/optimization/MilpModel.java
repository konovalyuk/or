package com.mavan.or.newpost.optimization;

import java.util.*;

final class MilpModel {
    private final int variableCount;
    private final double[] objectiveMin;
    private final boolean[] integer;
    private final double[] upperBounds;
    private final List<LinearConstraint> constraints = new ArrayList<>();

    MilpModel(int variableCount) {
        this.variableCount = variableCount;
        this.objectiveMin = new double[variableCount];
        this.integer = new boolean[variableCount];
        this.upperBounds = new double[variableCount];
        Arrays.fill(upperBounds, Double.POSITIVE_INFINITY);
    }

    int variableCount() { return variableCount; }
    double[] objectiveMin() { return objectiveMin; }
    boolean[] integer() { return integer; }
    double[] upperBounds() { return upperBounds; }
    List<LinearConstraint> constraints() { return constraints; }

    void setObjective(int variable, double coefficient) { objectiveMin[variable] = coefficient; }
    void setInteger(int variable) { integer[variable] = true; }
    void setUpperBound(int variable, double value) { upperBounds[variable] = value; }

    void addLessEqual(Map<Integer, Double> terms, double rhs) {
        double[] a = new double[variableCount];
        for (Map.Entry<Integer, Double> e : terms.entrySet()) a[e.getKey()] += e.getValue();
        constraints.add(new LinearConstraint(a, rhs));
    }

    void addLessEqual(int[] indexes, double[] coefficients, double rhs) {
        double[] a = new double[variableCount];
        for (int i = 0; i < indexes.length; i++) a[indexes[i]] += coefficients[i];
        constraints.add(new LinearConstraint(a, rhs));
    }

    void addEquality(Map<Integer, Double> terms, double rhs) {
        addLessEqual(terms, rhs);
        Map<Integer, Double> neg = new HashMap<>();
        for (Map.Entry<Integer, Double> e : terms.entrySet()) neg.put(e.getKey(), -e.getValue());
        addLessEqual(neg, -rhs);
    }
}
