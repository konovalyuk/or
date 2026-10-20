package com.mavan.or.newpost.model;

import java.util.List;

public final class ShipmentFlow {
    private final int origin;
    private final int destination;
    private final double tonnage;
    private final List<Integer> path;

    public ShipmentFlow(int origin, int destination, double tonnage, List<Integer> path) {
        this.origin = origin;
        this.destination = destination;
        this.tonnage = tonnage;
        this.path = List.copyOf(path);
    }

    public int getOrigin() { return origin; }
    public int getDestination() { return destination; }
    public double getTonnage() { return tonnage; }
    public List<Integer> getPath() { return path; }
}
