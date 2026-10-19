package com.mavan.or.algorithms.network.maximumflow;

import com.mavan.or.models.NetworkProblem;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Taha, Section 6.4.2. The labeling procedure that grows a path from the source
 * by the largest residual, breaks ties by the smallest node, and backtracks when
 * a node has nowhere left to go.
 */
@Service
public class FordFulkerson {

    private static final double EPS = 1e-9;

    public record FlowLabel(int node, boolean infinite, double amount, Integer predecessor, boolean crossedOut) {
    }

    public record ArcFlow(int from, int to, double amount) {
    }

    public record Iteration(
            int number,
            String title,
            List<String> steps,
            List<FlowLabel> labels,
            List<Integer> path,
            Double flow,
            Double[][] residualBefore,
            Double[][] residualAfter
    ) {
    }

    public record Result(double flow, String message, List<ArcFlow> arcs, List<Iteration> iterations) {
    }

    public Result solve(NetworkProblem problem) {
        int nodes = problem.nodes();
        double[][] design = capacities(problem);
        double[][] residual = copy(design);
        List<Iteration> iterations = new ArrayList<>();
        iterations.add(new Iteration(0, "Initial residuals",
                List.of("Residuals start equal to the design capacities. Node 1 is the source and node " + nodes + " is the sink."),
                List.of(), List.of(), null, matrix(residual), matrix(residual)));
        double total = 0;
        int guard = 0;
        while (guard++ <= nodes * nodes) {
            Search search = breakthrough(residual);
            if (search.path.isEmpty()) {
                iterations.add(new Iteration(iterations.size(), "No breakthrough",
                        search.steps, search.labels, List.of(), null, matrix(residual), matrix(residual)));
                break;
            }
            double flow = search.flow;
            Double[][] before = matrix(residual);
            augment(residual, search.path, flow);
            total += flow;
            iterations.add(new Iteration(iterations.size(), "Breakthrough " + iterations.size(),
                    search.steps, search.labels, search.path, flow, before, matrix(residual)));
        }
        return new Result(total,
                "The maximal flow is the sum of the breakthroughs. An arc carries flow only where the residual fell below its design capacity.",
                flows(design, residual), List.copyOf(iterations));
    }

    private static Search breakthrough(double[][] residual) {
        int nodes = residual.length - 1;
        int sink = nodes;
        double[] amount = new double[nodes + 1];
        int[] predecessor = new int[nodes + 1];
        boolean[] labeled = new boolean[nodes + 1];
        boolean[] crossed = new boolean[nodes + 1];
        Arrays.fill(predecessor, -1);
        labeled[1] = true;
        amount[1] = Double.POSITIVE_INFINITY;
        List<String> steps = new ArrayList<>();
        steps.add("Set a1 = infinity and label source node 1 as [infinity, —].");
        int current = 1;
        while (true) {
            int next = -1;
            double best = 0;
            List<Integer> candidates = new ArrayList<>();
            for (int node = 1; node <= nodes; node++) {
                if (labeled[node] || crossed[node] || residual[current][node] <= EPS) {
                    continue;
                }
                candidates.add(node);
                if (residual[current][node] > best + EPS || (Math.abs(residual[current][node] - best) <= EPS && node < next)) {
                    best = residual[current][node];
                    next = node;
                }
            }
            if (next < 0) {
                if (current == 1) {
                    steps.add("Node 1 has no unlabeled neighbor with a positive residual, so there is no breakthrough.");
                    return new Search(steps, labels(amount, predecessor, labeled, crossed), List.of(), 0);
                }
                int back = predecessor[current];
                crossed[current] = true;
                steps.add("Node " + current + " has no open neighbor. Cross it out and return to node " + back + ".");
                current = back;
                continue;
            }
            labeled[next] = true;
            predecessor[next] = current;
            amount[next] = best;
            steps.add("From node " + current + " the largest residual among " + candidates + " labels node " + next
                    + " as [" + trim(best) + ", " + current + "].");
            if (next == sink) {
                List<Integer> path = path(predecessor, sink);
                double flow = amount[sink];
                for (int node : path) {
                    if (amount[node] < flow) {
                        flow = amount[node];
                    }
                }
                steps.add("Sink node " + sink + " is labeled. Breakthrough path " + path + " carries " + trim(flow) + ".");
                return new Search(steps, labels(amount, predecessor, labeled, crossed), path, flow);
            }
            current = next;
        }
    }

    private static void augment(double[][] residual, List<Integer> path, double flow) {
        for (int index = 0; index < path.size() - 1; index++) {
            int from = path.get(index);
            int to = path.get(index + 1);
            residual[from][to] -= flow;
            residual[to][from] += flow;
        }
    }

    private static List<ArcFlow> flows(double[][] design, double[][] residual) {
        List<ArcFlow> flows = new ArrayList<>();
        for (int from = 1; from < design.length; from++) {
            for (int to = 1; to < design.length; to++) {
                double amount = design[from][to] - residual[from][to];
                if (amount > EPS) {
                    flows.add(new ArcFlow(from, to, amount));
                }
            }
        }
        return flows;
    }

    private static List<FlowLabel> labels(double[] amount, int[] predecessor, boolean[] labeled, boolean[] crossed) {
        List<FlowLabel> labels = new ArrayList<>();
        for (int node = 1; node < amount.length; node++) {
            if (!labeled[node]) {
                continue;
            }
            Integer from = predecessor[node] < 0 ? null : predecessor[node];
            boolean infinite = amount[node] == Double.POSITIVE_INFINITY;
            labels.add(new FlowLabel(node, infinite, infinite ? 0 : amount[node], from, crossed[node]));
        }
        return labels;
    }

    private static List<Integer> path(int[] predecessor, int sink) {
        List<Integer> reversed = new ArrayList<>();
        for (int node = sink; node > 0; node = predecessor[node]) {
            reversed.add(node);
            if (predecessor[node] < 0) {
                break;
            }
        }
        List<Integer> path = new ArrayList<>();
        for (int index = reversed.size() - 1; index >= 0; index--) {
            path.add(reversed.get(index));
        }
        return path;
    }

    private static double[][] capacities(NetworkProblem problem) {
        int nodes = problem.nodes();
        double[][] capacity = new double[nodes + 1][nodes + 1];
        for (NetworkProblem.Arc arc : problem.arcs()) {
            if (arc.value() < -EPS) {
                throw new IllegalArgumentException("A capacity cannot be negative.");
            }
            capacity[arc.from()][arc.to()] = arc.value();
        }
        return capacity;
    }

    private static double[][] copy(double[][] source) {
        double[][] copy = new double[source.length][];
        for (int row = 0; row < source.length; row++) {
            copy[row] = Arrays.copyOf(source[row], source[row].length);
        }
        return copy;
    }

    private static Double[][] matrix(double[][] residual) {
        int nodes = residual.length - 1;
        Double[][] shown = new Double[nodes][nodes];
        for (int row = 1; row <= nodes; row++) {
            for (int column = 1; column <= nodes; column++) {
                if (row != column) {
                    shown[row - 1][column - 1] = residual[row][column];
                }
            }
        }
        return shown;
    }

    private static String trim(double value) {
        if (Math.abs(value - Math.rint(value)) < EPS) {
            return String.valueOf((long) Math.rint(value));
        }
        return String.valueOf(value);
    }

    private record Search(List<String> steps, List<FlowLabel> labels, List<Integer> path, double flow) {
    }
}
