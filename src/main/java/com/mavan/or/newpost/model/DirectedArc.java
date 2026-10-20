package com.mavan.or.newpost.model;

public final class DirectedArc {
    private final int from;
    private final int to;
    private final double distance;

    public DirectedArc(int from, int to, double distance) {
        if (from == to) throw new IllegalArgumentException("Self arc is not allowed");
        if (distance <= 0 || !Double.isFinite(distance)) throw new IllegalArgumentException("Arc distance must be positive");
        this.from = from;
        this.to = to;
        this.distance = distance;
    }

    public int getFrom() { return from; }
    public int getTo() { return to; }
    public double getDistance() { return distance; }
}
