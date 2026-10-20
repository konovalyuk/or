package com.mavan.or.newpost.scheduling;

import java.util.List;

/** Continuous-time schedule of a fixed routing and truck plan. */
public record ContinuousSchedule(
        boolean feasible,
        List<String> violations,
        List<PlannedTrip> trips,
        List<PlannedTerminalJob> terminalJobs,
        double completionHour
) {
    public ContinuousSchedule {
        violations = List.copyOf(violations);
        trips = List.copyOf(trips);
        terminalJobs = List.copyOf(terminalJobs);
    }
}
