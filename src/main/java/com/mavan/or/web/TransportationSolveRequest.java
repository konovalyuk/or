package com.mavan.or.web;

import com.mavan.or.algorithms.transportation.start.ETransBasicSolution;

/** Request body accepted from the transportation-problem editor. */
public record TransportationSolveRequest(
        double[][] costs,
        double[] supply,
        double[] demand,
        ETransBasicSolution initialMethod
) {
}
