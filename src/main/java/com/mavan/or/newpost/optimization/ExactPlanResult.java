package com.mavan.or.newpost.optimization;

import com.mavan.or.newpost.scheduling.PlannedTerminalJob;
import com.mavan.or.newpost.scheduling.PlannedTrip;
import com.mavan.or.newpost.scheduling.TerminalLoad;

import java.util.List;

/**
 * Final route, truck, terminal and schedule plan.
 *
 * Nullable numbers are null when no plan or no proven bound exists.
 */
public record ExactPlanResult(
        Status status,
        String message,
        String method,
        boolean globallyOptimal,
        Double totalCost,
        Double lowerBound,
        Double gapPercent,
        double transportCost,
        double terminalCost,
        double rentalCost,
        double activationCost,
        double totalTonnage,
        Double costPerTon,
        double totalTruckKm,
        int tripCount,
        double transshipmentTonnage,
        Double completionHour,
        List<RoutePlan> routes,
        List<PlannedTrip> trips,
        List<PlannedTerminalJob> terminalJobs,
        List<TerminalLoad> terminalLoads,
        List<String> proof,
        List<ExplanationSection> explanation,
        List<String> warnings,
        long searchNodes
) {
    public enum Status { OPTIMAL, FEASIBLE_NOT_PROVEN, INFEASIBLE, NO_SOLUTION_FOUND, MODEL_TOO_LARGE }

    public ExactPlanResult {
        routes = List.copyOf(routes);
        trips = List.copyOf(trips);
        terminalJobs = List.copyOf(terminalJobs);
        terminalLoads = List.copyOf(terminalLoads);
        proof = List.copyOf(proof);
        explanation = List.copyOf(explanation);
        warnings = List.copyOf(warnings);
    }

    public boolean hasPlan() { return totalCost != null; }
}
