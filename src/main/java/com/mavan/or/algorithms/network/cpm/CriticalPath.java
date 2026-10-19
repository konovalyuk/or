package com.mavan.or.algorithms.network.cpm;

import com.mavan.or.models.NetworkProblem;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Taha, Sections 6.5.2–6.5.5. Forward and backward passes, activity floats,
 * and the PERT mean-variance reading of the same network.
 */
@Service
public class CriticalPath {

    private static final double EPS = 1e-9;

    public record PassStep(int node, double value, Integer via, String formula) {
    }

    public record Activity(
            int from,
            int to,
            String symbol,
            double duration,
            double earliestStart,
            double latestCompletion,
            double totalFloat,
            double freeFloat,
            boolean critical,
            boolean redFlag
    ) {
    }

    public record PertArc(int from, int to, String symbol, double optimistic, double likely, double pessimistic, double mean, double variance) {
    }

    public record PertNode(int node, List<Integer> path, double mean, double standardDeviation, Double scheduled, Double deviate, Double probability) {
    }

    public record Result(
            double project,
            List<Integer> criticalPath,
            String message,
            List<PassStep> forward,
            List<PassStep> backward,
            List<Activity> activities,
            List<PertArc> estimates,
            List<PertNode> nodes
    ) {
    }

    public Result solve(NetworkProblem problem) {
        return solve(problem, null, null, null, null);
    }

    public Result solve(NetworkProblem problem, double[] optimistic, double[] likely, double[] pessimistic, double[] scheduled) {
        boolean pert = optimistic != null;
        List<NetworkProblem.Arc> arcs = problem.arcs();
        if (pert && (likely == null || pessimistic == null || optimistic.length != arcs.size()
                || likely.length != arcs.size() || pessimistic.length != arcs.size())) {
            throw new IllegalArgumentException("Each activity needs an optimistic, a most likely, and a pessimistic time.");
        }
        int nodes = problem.nodes();
        double[] duration = new double[arcs.size()];
        double[] variance = new double[arcs.size()];
        List<PertArc> estimates = new ArrayList<>();
        for (int index = 0; index < arcs.size(); index++) {
            if (pert) {
                double a = optimistic[index];
                double m = likely[index];
                double b = pessimistic[index];
                if (!Double.isFinite(a) || !Double.isFinite(m) || !Double.isFinite(b) || a < -EPS || m < -EPS || b < -EPS || a > m + EPS || m > b + EPS) {
                    throw new IllegalArgumentException("Activity " + (index + 1) + " needs 0 ≤ optimistic ≤ most likely ≤ pessimistic.");
                }
                duration[index] = (a + 4 * m + b) / 6.0;
                variance[index] = Math.pow((b - a) / 6.0, 2);
                estimates.add(new PertArc(arcs.get(index).from(), arcs.get(index).to(), arcs.get(index).symbol(), a, m, b, duration[index], variance[index]));
            } else {
                if (arcs.get(index).value() < -EPS) {
                    throw new IllegalArgumentException("An activity duration cannot be negative.");
                }
                duration[index] = arcs.get(index).value();
            }
        }
        List<Integer> order = topological(nodes, arcs);
        double[] earliest = new double[nodes + 1];
        List<PassStep> forward = new ArrayList<>();
        for (int node : order) {
            double best = 0;
            boolean incoming = false;
            Integer via = null;
            String formula = "□" + node + " = 0";
            for (int index = 0; index < arcs.size(); index++) {
                if (arcs.get(index).to() != node) {
                    continue;
                }
                double candidate = earliest[arcs.get(index).from()] + duration[index];
                if (!incoming || candidate > best + EPS) {
                    best = candidate;
                    via = arcs.get(index).from();
                    formula = "□" + node + " = □" + arcs.get(index).from() + " + " + trim(duration[index]);
                }
                incoming = true;
            }
            if (incoming) {
                earliest[node] = best;
            }
            forward.add(new PassStep(node, earliest[node], via, incoming ? formula + " = " + trim(earliest[node]) : formula));
        }
        double project = 0;
        for (int node = 1; node <= nodes; node++) {
            project = Math.max(project, earliest[node]);
        }
        double[] latest = new double[nodes + 1];
        Arrays.fill(latest, project);
        List<PassStep> backward = new ArrayList<>();
        for (int position = order.size() - 1; position >= 0; position--) {
            int node = order.get(position);
            boolean outgoing = false;
            Integer via = null;
            double best = project;
            String formula = "Δ" + node + " = " + trim(project);
            for (int index = 0; index < arcs.size(); index++) {
                if (arcs.get(index).from() != node) {
                    continue;
                }
                double candidate = latest[arcs.get(index).to()] - duration[index];
                if (!outgoing || candidate < best - EPS) {
                    best = candidate;
                    via = arcs.get(index).to();
                    formula = "Δ" + node + " = Δ" + arcs.get(index).to() + " − " + trim(duration[index]);
                }
                outgoing = true;
            }
            if (outgoing) {
                latest[node] = best;
            }
            backward.add(new PassStep(node, latest[node], via, outgoing ? formula + " = " + trim(latest[node]) : formula));
        }
        List<Activity> activities = new ArrayList<>();
        for (int index = 0; index < arcs.size(); index++) {
            NetworkProblem.Arc arc = arcs.get(index);
            double total = latest[arc.to()] - earliest[arc.from()] - duration[index];
            double free = earliest[arc.to()] - earliest[arc.from()] - duration[index];
            boolean critical = Math.abs(total) <= EPS;
            activities.add(new Activity(arc.from(), arc.to(), arc.symbol(), duration[index], earliest[arc.from()],
                    latest[arc.to()], total, free, critical, free < total - EPS));
        }
        List<PertNode> nodesReading = pert ? pertNodes(nodes, arcs, duration, variance, scheduled) : List.of();
        return new Result(project, criticalNodes(nodes, arcs, activities, earliest),
                pert ? "PERT replaces each duration by its mean. The probability uses the longest mean path into the node."
                        : "An activity is critical when its total float is zero.",
                forward, backward, activities, estimates, nodesReading);
    }

    private static List<PertNode> pertNodes(int nodes, List<NetworkProblem.Arc> arcs, double[] duration, double[] variance,
                                            double[] scheduled) {
        double[] mean = new double[nodes + 1];
        double[] spread = new double[nodes + 1];
        int[] predecessor = new int[nodes + 1];
        Arrays.fill(predecessor, -1);
        List<Integer> order = topological(nodes, arcs);
        for (int node : order) {
            for (int index = 0; index < arcs.size(); index++) {
                if (arcs.get(index).to() != node) {
                    continue;
                }
                int from = arcs.get(index).from();
                double candidate = mean[from] + duration[index];
                double candidateSpread = spread[from] + variance[index];
                boolean longer = candidate > mean[node] + EPS;
                boolean sameMean = Math.abs(candidate - mean[node]) <= EPS && candidateSpread > spread[node] + EPS;
                if (predecessor[node] < 0 || longer || sameMean) {
                    mean[node] = candidate;
                    spread[node] = candidateSpread;
                    predecessor[node] = from;
                }
            }
        }
        List<PertNode> reading = new ArrayList<>();
        for (int node = 1; node <= nodes; node++) {
            List<Integer> path = path(predecessor, node);
            double std = round2(Math.sqrt(spread[node]));
            Double target = scheduled == null || node - 1 >= scheduled.length || !Double.isFinite(scheduled[node - 1]) ? null : scheduled[node - 1];
            Double deviate = null;
            Double probability = null;
            if (target != null) {
                if (std <= EPS) {
                    deviate = 0.0;
                    probability = target + EPS >= mean[node] ? 1.0 : 0.0;
                } else {
                    deviate = round2((target - mean[node]) / std);
                    probability = normalCdf(deviate);
                }
            }
            reading.add(new PertNode(node, path, mean[node], std, target, deviate, probability));
        }
        return reading;
    }

    private static List<Integer> criticalNodes(int nodes, List<NetworkProblem.Arc> arcs, List<Activity> activities, double[] earliest) {
        List<Integer> path = new ArrayList<>();
        path.add(1);
        int guard = 0;
        int node = 1;
        while (guard++ <= nodes) {
            int next = 0;
            for (Activity activity : activities) {
                if (activity.critical() && activity.from() == node && (next == 0 || activity.to() < next)) {
                    next = activity.to();
                }
            }
            if (next == 0) {
                break;
            }
            path.add(next);
            node = next;
        }
        return path;
    }

    private static List<Integer> topological(int nodes, List<NetworkProblem.Arc> arcs) {
        int[] incoming = new int[nodes + 1];
        for (NetworkProblem.Arc arc : arcs) {
            incoming[arc.to()]++;
        }
        List<Integer> ready = new ArrayList<>();
        for (int node = 1; node <= nodes; node++) {
            if (incoming[node] == 0) {
                ready.add(node);
            }
        }
        List<Integer> order = new ArrayList<>();
        int cursor = 0;
        while (cursor < ready.size()) {
            int node = ready.get(cursor++);
            order.add(node);
            for (NetworkProblem.Arc arc : arcs) {
                if (arc.from() == node && --incoming[arc.to()] == 0) {
                    ready.add(arc.to());
                }
            }
        }
        if (order.size() != nodes) {
            throw new IllegalArgumentException("The project network has a cycle, so the forward pass cannot start.");
        }
        return order;
    }

    private static List<Integer> path(int[] predecessor, int node) {
        List<Integer> reversed = new ArrayList<>();
        int guard = 0;
        for (int cursor = node; cursor > 0 && guard++ <= predecessor.length; cursor = predecessor[cursor]) {
            reversed.add(cursor);
        }
        List<Integer> path = new ArrayList<>();
        for (int index = reversed.size() - 1; index >= 0; index--) {
            path.add(reversed.get(index));
        }
        return path;
    }

    static double normalCdf(double z) {
        double t = 1.0 / (1.0 + 0.2316419 * Math.abs(z));
        double density = 0.3989422804014327 * Math.exp(-0.5 * z * z);
        double polynomial = t * (0.319381530 + t * (-0.356563782 + t * (1.781477937 + t * (-1.821255978 + t * 1.330274429))));
        double tail = density * polynomial;
        return z >= 0 ? 1 - tail : tail;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static String trim(double value) {
        if (Math.abs(value - Math.rint(value)) < EPS) {
            return String.valueOf((long) Math.rint(value));
        }
        return String.format(java.util.Locale.US, "%.2f", value);
    }
}
