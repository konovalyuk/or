package com.mavan.or.web;

public record NetworkSolveRequest(
        int nodes,
        int[] from,
        int[] to,
        double[] value,
        String[] symbol,
        Integer start,
        double[] optimistic,
        double[] likely,
        double[] pessimistic,
        double[] scheduled
) {
}
