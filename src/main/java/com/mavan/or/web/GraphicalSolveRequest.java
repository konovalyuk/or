package com.mavan.or.web;

/** Request body for the two-variable graphical LP solver. */
public record GraphicalSolveRequest(
        String sense,
        double[] objective,
        double[][] coefficients,
        String[] relations,
        double[] rhs
) {
}
