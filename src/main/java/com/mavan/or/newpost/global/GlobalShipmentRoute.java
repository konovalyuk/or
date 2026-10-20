package com.mavan.or.newpost.global;

import java.util.List;

/** One time-feasible shipment route extracted from the joint solution. */
public final class GlobalShipmentRoute {
    private final int commodityIndex;
    private final int origin;
    private final int destination;
    private final double tonnage;
    private final List<Integer> path;
    private final double departureHour;
    private final double arrivalHour;
    private final double totalDistance;

    public GlobalShipmentRoute(
            int commodityIndex,
            int origin,
            int destination,
            double tonnage,
            List<Integer> path,
            double departureHour,
            double arrivalHour,
            double totalDistance
    ) {
        this.commodityIndex = commodityIndex;
        this.origin = origin;
        this.destination = destination;
        this.tonnage = tonnage;
        this.path = List.copyOf(path);
        this.departureHour = departureHour;
        this.arrivalHour = arrivalHour;
        this.totalDistance = totalDistance;
    }

    public int getCommodityIndex() { return commodityIndex; }
    public int getOrigin() { return origin; }
    public int getDestination() { return destination; }
    public double getTonnage() { return tonnage; }
    public List<Integer> getPath() { return path; }
    public double getDepartureHour() { return departureHour; }
    public double getArrivalHour() { return arrivalHour; }
    public double getTotalDistance() { return totalDistance; }
}
