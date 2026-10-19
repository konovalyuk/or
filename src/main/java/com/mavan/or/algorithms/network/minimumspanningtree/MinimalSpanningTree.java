package com.mavan.or.algorithms.network.minimumspanningtree;

import com.mavan.or.models.NetworkProblem;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Taha, Section 6.2. Grow a tree from a chosen node by the shortest link
 * that joins the connected set to a node still outside it.
 */
@Service
public class MinimalSpanningTree {

    private static final double EPS = 1e-9;

    public record Link(int from, int to, double length) {
    }

    public record Iteration(
            int number,
            List<Integer> connected,
            List<Integer> unconnected,
            List<Link> candidates,
            Link added,
            double length
    ) {
    }

    public record Result(int start, double length, boolean connected, String message, List<Link> tree, List<Iteration> iterations) {
    }

    public Result solve(NetworkProblem problem, int start) {
        int nodes = problem.nodes();
        if (start < 1 || start > nodes) {
            throw new IllegalArgumentException("The starting node is outside the network.");
        }
        double[][] length = matrix(problem);
        boolean[] inside = new boolean[nodes + 1];
        inside[start] = true;
        int connectedCount = 1;
        List<Link> tree = new ArrayList<>();
        List<Iteration> iterations = new ArrayList<>();
        iterations.add(snapshot(0, inside, List.of(), null, 0));
        double total = 0;
        while (connectedCount < nodes) {
            double best = Double.POSITIVE_INFINITY;
            int outside = 0;
            int endpoint = 0;
            List<Link> candidates = new ArrayList<>();
            for (int node = 1; node <= nodes; node++) {
                if (inside[node]) {
                    continue;
                }
                for (int other = 1; other <= nodes; other++) {
                    if (!inside[other] || length[other][node] == Double.POSITIVE_INFINITY) {
                        continue;
                    }
                    candidates.add(new Link(other, node, length[other][node]));
                    boolean closer = length[other][node] < best - EPS;
                    boolean tie = Math.abs(length[other][node] - best) <= EPS && (node < outside || (node == outside && other < endpoint));
                    if (closer || tie) {
                        best = length[other][node];
                        outside = node;
                        endpoint = other;
                    }
                }
            }
            if (outside == 0) {
                iterations.add(snapshot(iterations.size(), inside, candidates, null, total));
                return new Result(start, total, false,
                        "No arc reaches the remaining nodes, so the network is not connected.",
                        List.copyOf(tree), List.copyOf(iterations));
            }
            Link added = new Link(endpoint, outside, best);
            tree.add(added);
            total += best;
            inside[outside] = true;
            connectedCount++;
            iterations.add(snapshot(iterations.size(), inside, candidates, added, total));
        }
        return new Result(start, total, true,
                "The minimal spanning tree joins every node.",
                List.copyOf(tree), List.copyOf(iterations));
    }

    private static Iteration snapshot(int number, boolean[] inside, List<Link> candidates, Link added, double length) {
        List<Integer> connected = new ArrayList<>();
        List<Integer> unconnected = new ArrayList<>();
        for (int node = 1; node < inside.length; node++) {
            (inside[node] ? connected : unconnected).add(node);
        }
        return new Iteration(number, connected, unconnected, List.copyOf(candidates), added, length);
    }

    private static double[][] matrix(NetworkProblem problem) {
        int nodes = problem.nodes();
        double[][] length = new double[nodes + 1][nodes + 1];
        for (int row = 1; row <= nodes; row++) {
            for (int column = 1; column <= nodes; column++) {
                length[row][column] = Double.POSITIVE_INFINITY;
            }
        }
        for (NetworkProblem.Arc arc : problem.arcs()) {
            if (arc.value() < -EPS) {
                throw new IllegalArgumentException("A spanning-tree length cannot be negative.");
            }
            double existing = length[arc.from()][arc.to()];
            if (existing != Double.POSITIVE_INFINITY && Math.abs(existing - arc.value()) > EPS) {
                throw new IllegalArgumentException("The two directions of an undirected arc disagree.");
            }
            length[arc.from()][arc.to()] = arc.value();
            length[arc.to()][arc.from()] = arc.value();
        }
        return length;
    }
}
