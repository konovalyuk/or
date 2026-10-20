package com.mavan.or.web;

import com.mavan.or.newpost.optimization.ExactPlanResult;
import com.mavan.or.newpost.optimization.ExactPlanService;

import java.util.List;
import java.util.Map;

/** HTTP response: the requested plan plus comparison scenarios. */
public record NetworkOptimizationResponse(
        List<String> locations,
        String currency,
        String readyTime,
        ExactPlanResult plan,
        Map<String, ExactPlanResult> scenarios,
        Double savingsVsDirect,
        Double savingsPercent
) {
    static NetworkOptimizationResponse from(List<String> locations, String currency, String readyTime,
                                            ExactPlanService.Scenarios scenarios) {
        return new NetworkOptimizationResponse(locations, currency, readyTime, scenarios.plan(),
                scenarios.scenarios(), scenarios.savingsVsDirect(), scenarios.savingsPercent());
    }
}
