package com.mavan.or.newpost.model;

public final class Commodity {
    private final int origin;
    private final int destination;
    private final double demand;

    public Commodity(int origin, int destination, double demand) {
        if (origin == destination) throw new IllegalArgumentException("Commodity origin must differ from destination");
        if (demand <= 0 || !Double.isFinite(demand)) throw new IllegalArgumentException("Commodity demand must be positive");
        this.origin = origin;
        this.destination = destination;
        this.demand = demand;
    }

    public int getOrigin() { return origin; }
    public int getDestination() { return destination; }
    public double getDemand() { return demand; }
}
