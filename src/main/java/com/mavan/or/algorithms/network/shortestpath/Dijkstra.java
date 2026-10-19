package com.mavan.or.algorithms.network.shortestpath;

import com.mavan.or.models.NetworkProblem;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Taha, Section 6.3.2. Dijkstra's labeling: temporary labels are replaced only
 * by a strictly shorter route, then the smallest temporary label becomes permanent.
 */
@Service
public class Dijkstra {

    private static final double EPS = 1e-9;

    public record Label(Double distance, Integer predecessor, String status) {
    }

    public record Iteration(int number, int permanentNode, List<Label> labels) {
    }

    public record Route(int node, Double distance, List<Integer> nodes) {
    }

    public record Result(int source, String message, List<Iteration> iterations, List<Route> routes) {
    }

    public Result solve(NetworkProblem problem, int source) {
        int nodes = problem.nodes();
        if (source < 1 || source > nodes) {
            throw new IllegalArgumentException("The source node is outside the network.");
        }
        double[][] length = directed(problem, true);
        double[] distance = new double[nodes + 1];
        int[] predecessor = new int[nodes + 1];
        boolean[] permanent = new boolean[nodes + 1];
        Arrays.fill(distance, Double.POSITIVE_INFINITY);
        Arrays.fill(predecessor, -1);
        distance[source] = 0;
        permanent[source] = true;
        List<Iteration> iterations = new ArrayList<>();
        scan(length, distance, predecessor, permanent, source);
        iterations.add(new Iteration(1, source, labels(distance, predecessor, permanent)));
        for (int step = 2; step <= nodes; step++) {
            int chosen = smallestTemporary(distance, permanent);
            if (chosen < 0) {
                break;
            }
            permanent[chosen] = true;
            scan(length, distance, predecessor, permanent, chosen);
            iterations.add(new Iteration(step, chosen, labels(distance, predecessor, permanent)));
        }
        List<Route> routes = new ArrayList<>();
        boolean all = true;
        for (int node = 1; node <= nodes; node++) {
            if (distance[node] == Double.POSITIVE_INFINITY) {
                all = false;
                routes.add(new Route(node, null, List.of()));
            } else {
                routes.add(new Route(node, distance[node], path(predecessor, source, node)));
            }
        }
        String message = all
                ? "Every node has a permanent label."
                : "Some nodes cannot be reached from the source, so they stay unlabeled.";
        return new Result(source, message, List.copyOf(iterations), List.copyOf(routes));
    }

    private static void scan(double[][] length, double[] distance, int[] predecessor, boolean[] permanent, int from) {
        for (int to = 1; to < distance.length; to++) {
            if (permanent[to] || length[from][to] == Double.POSITIVE_INFINITY) {
                continue;
            }
            double candidate = distance[from] + length[from][to];
            if (candidate < distance[to] - EPS) {
                distance[to] = candidate;
                predecessor[to] = from;
            }
        }
    }

    private static int smallestTemporary(double[] distance, boolean[] permanent) {
        int chosen = -1;
        double best = Double.POSITIVE_INFINITY;
        for (int node = 1; node < distance.length; node++) {
            if (permanent[node] || distance[node] == Double.POSITIVE_INFINITY) {
                continue;
            }
            if (distance[node] < best - EPS) {
                best = distance[node];
                chosen = node;
            }
        }
        return chosen;
    }

    private static List<Label> labels(double[] distance, int[] predecessor, boolean[] permanent) {
        List<Label> labels = new ArrayList<>();
        for (int node = 1; node < distance.length; node++) {
            if (distance[node] == Double.POSITIVE_INFINITY) {
                labels.add(new Label(null, null, null));
            } else {
                Integer from = predecessor[node] < 0 ? null : predecessor[node];
                labels.add(new Label(distance[node], from, permanent[node] ? "PERMANENT" : "TEMPORARY"));
            }
        }
        return labels;
    }

    private static List<Integer> path(int[] predecessor, int source, int node) {
        List<Integer> reversed = new ArrayList<>();
        int guard = 0;
        for (int cursor = node; cursor > 0 && guard++ <= predecessor.length; cursor = predecessor[cursor]) {
            reversed.add(cursor);
            if (cursor == source) {
                break;
            }
        }
        List<Integer> path = new ArrayList<>();
        for (int index = reversed.size() - 1; index >= 0; index--) {
            path.add(reversed.get(index));
        }
        return path;
    }

    static double[][] directed(NetworkProblem problem, boolean nonnegative) {
        int nodes = problem.nodes();
        double[][] length = new double[nodes + 1][nodes + 1];
        for (double[] row : length) {
            Arrays.fill(row, Double.POSITIVE_INFINITY);
        }
        for (NetworkProblem.Arc arc : problem.arcs()) {
            if (nonnegative && arc.value() < -EPS) {
                throw new IllegalArgumentException("Dijkstra's algorithm needs nonnegative arc lengths.");
            }
            length[arc.from()][arc.to()] = arc.value();
        }
        return length;
    }
}
