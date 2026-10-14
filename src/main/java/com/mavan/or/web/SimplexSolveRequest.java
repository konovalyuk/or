package com.mavan.or.web;

/**
 * Request body for the simplex solver.
 */
public record SimplexSolveRequest(
        String sense,
        double[] objective,
        double[][] coefficients,
        String[] relations,
        double[] rhs,
        String method
) {
}
