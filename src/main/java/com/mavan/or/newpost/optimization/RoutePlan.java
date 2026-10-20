package com.mavan.or.newpost.optimization;

import java.util.List;

/** One routed part of an origin-destination flow in the final plan. */
public record RoutePlan(
        int shipmentIndex,
        int origin,
        int destination,
        double tonnage,
        List<Integer> path,
        double distance,
        double firstDepartureHour,
        double deliveredHour,
        double handlingCost
) {
    public RoutePlan {
        path = List.copyOf(path);
    }

    public boolean transshipment() { return path.size() > 2; }
}
