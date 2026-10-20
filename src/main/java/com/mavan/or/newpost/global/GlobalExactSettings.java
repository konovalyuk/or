package com.mavan.or.newpost.global;

/**
 * Controls the joint route-and-schedule exact model.
 *
 * The global optimizer now uses a sparse MILP backend.  The limits below are
 * safety limits for model dimensions/nonzeros; they are not dense-tableau
 * limits and do not change the mathematical model.
 */
public final class GlobalExactSettings {
    private final long nodeLimit;
    private final int maxVariables;
    private final int maxConstraints;
    private final long maxSparseNonZeros;

    public GlobalExactSettings(long nodeLimit, int maxVariables, int maxConstraints) {
        this(nodeLimit, maxVariables, maxConstraints, 10_000_000L);
    }

    /**
     * Backward-compatible constructor. The fourth argument used to mean a
     * dense tableau-cell limit; it is now interpreted as a sparse nonzero limit.
     */
    public GlobalExactSettings(long nodeLimit, int maxVariables, int maxConstraints,
                               long maxSparseNonZeros) {
        if (nodeLimit <= 0) throw new IllegalArgumentException("Node limit must be positive");
        if (maxVariables <= 0 || maxConstraints <= 0 || maxSparseNonZeros <= 0) {
            throw new IllegalArgumentException("Model-size limits must be positive");
        }
        this.nodeLimit = nodeLimit;
        this.maxVariables = maxVariables;
        this.maxConstraints = maxConstraints;
        this.maxSparseNonZeros = maxSparseNonZeros;
    }

    public long getNodeLimit() { return nodeLimit; }
    public int getMaxVariables() { return maxVariables; }
    public int getMaxConstraints() { return maxConstraints; }
    public long getMaxSparseNonZeros() { return maxSparseNonZeros; }

    /**
     * Full New Post 1-hour instances fit within these sparse model limits.
     */
    public static GlobalExactSettings defaults() {
        return new GlobalExactSettings(
                1_000_000L,
                100_000,
                50_000,
                10_000_000L
        );
    }
}
