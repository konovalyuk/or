package com.mavan.or.models;

import java.util.ArrayList;
import java.util.List;

/**
 * A network of nodes 1..n and directed arcs.
 * A missing opposite arc is not stored. Undirected models read each arc both ways.
 */
public final class NetworkProblem {

    public record Arc(int from, int to, double value, String symbol) {
    }

    private final int nodes;
    private final List<Arc> arcs;

    private NetworkProblem(int nodes, List<Arc> arcs) {
        this.nodes = nodes;
        this.arcs = List.copyOf(arcs);
    }

    public static NetworkProblem of(int nodes, int[] from, int[] to, double[] value, String[] symbol) {
        if (nodes < 2 || nodes > 12) {
            throw new IllegalArgumentException("A network needs between 2 and 12 nodes.");
        }
        if (from == null || to == null || value == null || from.length != to.length || from.length != value.length) {
            throw new IllegalArgumentException("Each arc needs a start, an end, and a value.");
        }
        if (symbol != null && symbol.length != from.length) {
            throw new IllegalArgumentException("Each arc needs a symbol when symbols are sent.");
        }
        List<Arc> arcs = new ArrayList<>();
        for (int index = 0; index < from.length; index++) {
            int start = from[index];
            int end = to[index];
            if (start < 1 || start > nodes || end < 1 || end > nodes) {
                throw new IllegalArgumentException("Arc " + (index + 1) + " points outside the network.");
            }
            if (start == end) {
                throw new IllegalArgumentException("Arc " + (index + 1) + " cannot join a node to itself.");
            }
            double amount = value[index];
            if (!Double.isFinite(amount)) {
                throw new IllegalArgumentException("Arc " + (index + 1) + " must have a finite value.");
            }
            String name = symbol == null || symbol[index] == null ? "" : symbol[index].trim();
            arcs.add(new Arc(start, end, amount, name));
        }
        return new NetworkProblem(nodes, arcs);
    }

    public int nodes() {
        return nodes;
    }

    public List<Arc> arcs() {
        return arcs;
    }
}
