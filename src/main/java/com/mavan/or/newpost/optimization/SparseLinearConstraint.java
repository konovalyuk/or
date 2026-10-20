package com.mavan.or.newpost.optimization;

/** Immutable sparse <= constraint used by the sparse MILP backend. */
public final class SparseLinearConstraint {
    private final int[] indexes;
    private final double[] coefficients;
    private final double rhs;

    public SparseLinearConstraint(int[] indexes, double[] coefficients, double rhs) {
        if (indexes == null || coefficients == null || indexes.length != coefficients.length) {
            throw new IllegalArgumentException("Sparse constraint arrays must have the same size");
        }
        this.indexes = indexes.clone();
        this.coefficients = coefficients.clone();
        this.rhs = rhs;
        for (int i = 0; i < this.indexes.length; i++) {
            if (this.indexes[i] < 0) throw new IllegalArgumentException("Negative variable index");
            if (!Double.isFinite(this.coefficients[i])) throw new IllegalArgumentException("Non-finite coefficient");
        }
        if (!Double.isFinite(rhs)) throw new IllegalArgumentException("Non-finite RHS");
    }

    public int[] indexes() { return indexes.clone(); }
    public double[] coefficients() { return coefficients.clone(); }
    public double rhs() { return rhs; }

    int size() { return indexes.length; }

    double coefficientAt(int position) { return coefficients[position]; }
    int indexAt(int position) { return indexes[position]; }
}
