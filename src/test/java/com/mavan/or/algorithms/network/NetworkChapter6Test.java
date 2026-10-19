package com.mavan.or.algorithms.network;

import com.mavan.or.algorithms.network.cpm.CriticalPath;
import com.mavan.or.algorithms.network.maximumflow.FordFulkerson;
import com.mavan.or.algorithms.network.minimumspanningtree.MinimalSpanningTree;
import com.mavan.or.algorithms.network.shortestpath.Dijkstra;
import com.mavan.or.algorithms.network.shortestpath.Floyd;
import com.mavan.or.models.NetworkProblem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkChapter6Test {

    private static final double EPS = 1e-6;

    @Test
    void spanningTreeOfFigure65GrowsFromNode1() {
        NetworkProblem network = undirected(6, new int[][]{
                {1, 2, 1}, {1, 3, 5}, {1, 4, 7}, {1, 5, 9},
                {2, 3, 6}, {2, 4, 4}, {2, 5, 3},
                {3, 4, 5}, {3, 6, 10},
                {4, 5, 8}, {4, 6, 3}
        });
        MinimalSpanningTree.Result result = new MinimalSpanningTree().solve(network, 1);
        assertEquals(16, result.length(), EPS);
        assertTrue(result.connected());
        assertEquals(List.of(link(1, 2, 1), link(2, 5, 3), link(2, 4, 4), link(4, 6, 3), link(1, 3, 5)), result.tree());
    }

    @Test
    void dijkstraMatchesExample634AndToraLabels() {
        NetworkProblem network = directed(5, new double[][]{
                {1, 2, 100}, {1, 3, 30}, {2, 3, 20}, {3, 4, 10}, {3, 5, 60}, {4, 2, 15}, {4, 5, 50}
        });
        Dijkstra.Result result = new Dijkstra().solve(network, 1);
        assertEquals(5, result.iterations().size());
        assertLabel(result, 0, 0, 0, null, "PERMANENT");
        assertLabel(result, 0, 1, 100, 1, "TEMPORARY");
        assertLabel(result, 0, 2, 30, 1, "TEMPORARY");
        assertLabel(result, 1, 2, 30, 1, "PERMANENT");
        assertLabel(result, 1, 3, 40, 3, "TEMPORARY");
        assertLabel(result, 1, 4, 90, 3, "TEMPORARY");
        assertLabel(result, 2, 1, 55, 4, "TEMPORARY");
        assertLabel(result, 2, 4, 90, 3, "TEMPORARY");
        assertLabel(result, 4, 4, 90, 3, "PERMANENT");
        assertRoute(result, 2, 55, List.of(1, 3, 4, 2));
        assertRoute(result, 3, 30, List.of(1, 3));
        assertRoute(result, 4, 40, List.of(1, 3, 4));
        assertRoute(result, 5, 90, List.of(1, 3, 5));
    }

    @Test
    void floydMatchesExample635() {
        NetworkProblem network = directed(5, new double[][]{
                {1, 2, 3}, {2, 1, 3}, {1, 3, 10}, {3, 1, 10},
                {2, 4, 5}, {4, 2, 5}, {3, 4, 6}, {4, 3, 6},
                {3, 5, 15}, {4, 5, 4}, {5, 4, 4}
        });
        Floyd.Result result = new Floyd().solve(network);
        Double[][] last = result.iterations().get(4).distance();
        Integer[][] sequence = result.iterations().get(4).sequence();
        assertEquals(12, last[0][4], EPS);
        assertEquals(8, last[0][3], EPS);
        assertEquals(11, last[1][2], EPS);
        assertEquals(9, last[1][4], EPS);
        assertEquals(10, last[2][4], EPS);
        assertNull(result.iterations().get(0).distance()[4][2]);
        assertEquals(4, sequence[0][4]);
        assertEquals(2, sequence[0][3]);
        Floyd.Route route = result.routes().stream().filter(item -> item.from() == 1 && item.to() == 5).findFirst().orElseThrow();
        assertEquals(12, route.distance(), EPS);
        assertEquals(List.of(1, 2, 4, 5), route.nodes());
        assertTrue(result.iterations().get(5).improved().isEmpty());
    }

    @Test
    void maximalFlowMatchesExample642() {
        NetworkProblem network = directed(5, new double[][]{
                {1, 2, 20}, {1, 3, 30}, {1, 4, 10},
                {2, 3, 40}, {2, 5, 30},
                {3, 4, 10}, {4, 3, 5}, {3, 5, 20},
                {4, 5, 20}
        });
        FordFulkerson.Result result = new FordFulkerson().solve(network);
        assertEquals(60, result.flow(), EPS);
        assertEquals(List.of(1, 3, 5), result.iterations().get(1).path());
        assertEquals(20, result.iterations().get(1).flow(), EPS);
        assertEquals(List.of(1, 2, 3, 4, 5), result.iterations().get(2).path());
        assertEquals(10, result.iterations().get(2).flow(), EPS);
        assertEquals(List.of(1, 2, 5), result.iterations().get(3).path());
        assertEquals(10, result.iterations().get(3).flow(), EPS);
        assertEquals(List.of(1, 3, 2, 5), result.iterations().get(4).path());
        assertEquals(10, result.iterations().get(4).flow(), EPS);
        assertEquals(List.of(1, 4, 5), result.iterations().get(5).path());
        assertEquals(10, result.iterations().get(5).flow(), EPS);
        assertTrue(result.iterations().get(6).path().isEmpty());
        assertFlow(result, 1, 2, 20);
        assertFlow(result, 1, 3, 30);
        assertFlow(result, 1, 4, 10);
        assertFlow(result, 2, 5, 20);
        assertFlow(result, 3, 4, 10);
        assertFlow(result, 3, 5, 20);
        assertFlow(result, 4, 5, 20);
        assertTrue(result.arcs().stream().noneMatch(arc -> arc.from() == 2 && arc.to() == 3));
        assertTrue(result.arcs().stream().noneMatch(arc -> arc.from() == 4 && arc.to() == 3));
    }

    @Test
    void criticalPathMatchesExamples652And654() {
        CriticalPath.Result result = new CriticalPath().solve(project());
        assertEquals(25, result.project(), EPS);
        assertEquals(List.of(1, 2, 4, 5, 6), result.criticalPath());
        assertPass(result.forward(), 1, 0);
        assertPass(result.forward(), 2, 5);
        assertPass(result.forward(), 3, 8);
        assertPass(result.forward(), 4, 13);
        assertPass(result.forward(), 5, 13);
        assertPass(result.forward(), 6, 25);
        assertPass(result.backward(), 6, 25);
        assertPass(result.backward(), 5, 13);
        assertPass(result.backward(), 4, 13);
        assertPass(result.backward(), 3, 11);
        assertPass(result.backward(), 2, 5);
        assertPass(result.backward(), 1, 0);
        assertVia(result.forward(), 1, null);
        assertVia(result.forward(), 4, 2);
        assertVia(result.forward(), 6, 5);
        assertVia(result.backward(), 6, null);
        assertVia(result.backward(), 3, 5);
        assertVia(result.backward(), 2, 4);
        assertActivity(result, "B", 0, 11, 5, 2, false);
        assertActivity(result, "C", 5, 11, 3, 0, false);
        assertActivity(result, "E", 8, 13, 3, 3, false);
        assertActivity(result, "F", 8, 25, 6, 6, false);
        assertActivity(result, "G", 13, 25, 11, 11, false);
        assertActivity(result, "A", 0, 5, 0, 0, true);
        assertActivity(result, "H", 13, 25, 0, 0, true);
        CriticalPath.Activity dummy = result.activities().stream().filter(item -> item.from() == 4 && item.to() == 5).findFirst().orElseThrow();
        assertTrue(dummy.critical());
        assertEquals(0, dummy.duration(), EPS);
    }

    @Test
    void pertMatchesExample656() {
        double[][] estimates = {
                {3, 5, 7}, {4, 6, 8}, {1, 3, 5}, {5, 8, 11}, {1, 2, 3},
                {9, 11, 13}, {1, 1, 1}, {10, 12, 14}, {0, 0, 0}
        };
        double[] optimistic = column(estimates, 0);
        double[] likely = column(estimates, 1);
        double[] pessimistic = column(estimates, 2);
        CriticalPath.Result result = new CriticalPath().solve(project(), optimistic, likely, pessimistic, new double[]{0, 5, 11, 12, 14, 26});
        assertPert(result, "A", 5, 4.0 / 9);
        assertPert(result, "D", 8, 1);
        assertPert(result, "G", 1, 0);
        CriticalPath.PertNode node2 = result.nodes().get(1);
        assertEquals(List.of(1, 2), node2.path());
        assertEquals(5, node2.mean(), EPS);
        assertEquals(0.67, node2.standardDeviation(), EPS);
        assertEquals(0, node2.deviate(), EPS);
        assertEquals(0.5, node2.probability(), 0.01);
        CriticalPath.PertNode node3 = result.nodes().get(2);
        assertEquals(List.of(1, 2, 3), node3.path());
        assertEquals(8, node3.mean(), EPS);
        assertEquals(0.94, node3.standardDeviation(), EPS);
        assertEquals(3.19, node3.deviate(), EPS);
        assertEquals(0.9993, node3.probability(), 0.002);
        CriticalPath.PertNode node6 = result.nodes().get(5);
        assertEquals(List.of(1, 2, 4, 5, 6), node6.path());
        assertEquals(25, node6.mean(), EPS);
        assertEquals(1.37, node6.standardDeviation(), EPS);
        assertEquals(0.73, node6.deviate(), EPS);
        assertEquals(0.7673, node6.probability(), 0.002);
        assertEquals(0.2033, result.nodes().get(3).probability(), 0.002);
        assertEquals(0.7967, result.nodes().get(4).probability(), 0.002);
    }

    private static void assertLabel(Dijkstra.Result result, int iteration, int node, double distance, Integer predecessor, String status) {
        Dijkstra.Label label = result.iterations().get(iteration).labels().get(node);
        assertEquals(distance, label.distance(), EPS);
        assertEquals(predecessor, label.predecessor());
        assertEquals(status, label.status());
    }

    private static void assertRoute(Dijkstra.Result result, int node, double distance, List<Integer> path) {
        Dijkstra.Route route = result.routes().get(node - 1);
        assertEquals(distance, route.distance(), EPS);
        assertEquals(path, route.nodes());
    }

    private static void assertFlow(FordFulkerson.Result result, int from, int to, double amount) {
        FordFulkerson.ArcFlow flow = result.arcs().stream()
                .filter(arc -> arc.from() == from && arc.to() == to).findFirst().orElseThrow();
        assertEquals(amount, flow.amount(), EPS);
    }

    private static void assertPass(List<CriticalPath.PassStep> steps, int node, double value) {
        CriticalPath.PassStep step = steps.stream().filter(item -> item.node() == node).findFirst().orElseThrow();
        assertEquals(value, step.value(), EPS);
    }

    private static void assertVia(List<CriticalPath.PassStep> steps, int node, Integer via) {
        CriticalPath.PassStep step = steps.stream().filter(item -> item.node() == node).findFirst().orElseThrow();
        assertEquals(via, step.via());
    }

    private static void assertActivity(CriticalPath.Result result, String symbol, double start, double completion,
                                       double total, double free, boolean critical) {
        CriticalPath.Activity activity = result.activities().stream().filter(item -> symbol.equals(item.symbol())).findFirst().orElseThrow();
        assertEquals(start, activity.earliestStart(), EPS);
        assertEquals(completion, activity.latestCompletion(), EPS);
        assertEquals(total, activity.totalFloat(), EPS);
        assertEquals(free, activity.freeFloat(), EPS);
        assertEquals(critical, activity.critical());
    }

    private static void assertPert(CriticalPath.Result result, String symbol, double mean, double variance) {
        CriticalPath.PertArc arc = result.estimates().stream().filter(item -> symbol.equals(item.symbol())).findFirst().orElseThrow();
        assertEquals(mean, arc.mean(), EPS);
        assertEquals(variance, arc.variance(), EPS);
    }

    private static MinimalSpanningTree.Link link(int from, int to, double length) {
        return new MinimalSpanningTree.Link(from, to, length);
    }

    private static NetworkProblem project() {
        return directed(6, new double[][]{
                {1, 2, 5}, {1, 3, 6}, {2, 3, 3}, {2, 4, 8}, {3, 5, 2},
                {3, 6, 11}, {4, 6, 1}, {5, 6, 12}, {4, 5, 0}
        }, new String[]{"A", "B", "C", "D", "E", "F", "G", "H", ""});
    }

    private static double[] column(double[][] rows, int column) {
        double[] values = new double[rows.length];
        for (int index = 0; index < rows.length; index++) {
            values[index] = rows[index][column];
        }
        return values;
    }

    private static NetworkProblem undirected(int nodes, int[][] edges) {
        int[] from = new int[edges.length];
        int[] to = new int[edges.length];
        double[] value = new double[edges.length];
        for (int index = 0; index < edges.length; index++) {
            from[index] = edges[index][0];
            to[index] = edges[index][1];
            value[index] = edges[index][2];
        }
        return NetworkProblem.of(nodes, from, to, value, null);
    }

    private static NetworkProblem directed(int nodes, double[][] arcs) {
        return directed(nodes, arcs, null);
    }

    private static NetworkProblem directed(int nodes, double[][] arcs, String[] symbol) {
        int[] from = new int[arcs.length];
        int[] to = new int[arcs.length];
        double[] value = new double[arcs.length];
        for (int index = 0; index < arcs.length; index++) {
            from[index] = (int) arcs[index][0];
            to[index] = (int) arcs[index][1];
            value[index] = arcs[index][2];
        }
        return NetworkProblem.of(nodes, from, to, value, symbol);
    }
}
