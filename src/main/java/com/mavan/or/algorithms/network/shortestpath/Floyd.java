package com.mavan.or.algorithms.network.shortestpath;

import com.mavan.or.models.NetworkProblem;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Taha, Section 6.3.2. Floyd's triple operation on the distance and sequence matrices.
 */
@Service
public class Floyd {

    private static final double EPS = 1e-9;

    public record Cell(int row, int column) {
    }

    public record Iteration(int pivot, Double[][] distance, Integer[][] sequence, List<Cell> improved) {
    }

    public record Route(int from, int to, Double distance, List<Integer> nodes) {
    }

    public record Result(String message, List<Iteration> iterations, List<Route> routes) {
    }

    public Result solve(NetworkProblem problem) {
        int nodes = problem.nodes();
        double[][] distance = Dijkstra.directed(problem, false);
        int[][] sequence = new int[nodes + 1][nodes + 1];
        for (int row = 1; row <= nodes; row++) {
            for (int column = 1; column <= nodes; column++) {
                sequence[row][column] = column;
            }
            distance[row][row] = 0;
        }
        List<Iteration> iterations = new ArrayList<>();
        iterations.add(snapshot(0, distance, sequence, List.of()));
        for (int pivot = 1; pivot <= nodes; pivot++) {
            List<Cell> improved = new ArrayList<>();
            for (int row = 1; row <= nodes; row++) {
                if (row == pivot || distance[row][pivot] == Double.POSITIVE_INFINITY) {
                    continue;
                }
                for (int column = 1; column <= nodes; column++) {
                    if (column == pivot || column == row || distance[pivot][column] == Double.POSITIVE_INFINITY) {
                        continue;
                    }
                    double through = distance[row][pivot] + distance[pivot][column];
                    if (through < distance[row][column] - EPS) {
                        distance[row][column] = through;
                        sequence[row][column] = pivot;
                        improved.add(new Cell(row, column));
                    }
                }
            }
            iterations.add(snapshot(pivot, distance, sequence, improved));
        }
        List<Route> routes = new ArrayList<>();
        for (int from = 1; from <= nodes; from++) {
            for (int to = 1; to <= nodes; to++) {
                if (from == to) {
                    continue;
                }
                if (distance[from][to] == Double.POSITIVE_INFINITY) {
                    routes.add(new Route(from, to, null, List.of()));
                } else {
                    routes.add(new Route(from, to, distance[from][to], route(sequence, from, to)));
                }
            }
        }
        return new Result("The final matrices give the shortest route between every pair of nodes.", iterations, routes);
    }

    private static List<Integer> route(int[][] sequence, int from, int to) {
        List<Integer> nodes = new ArrayList<>();
        expand(sequence, nodes, from, to, 0);
        return nodes;
    }

    private static void expand(int[][] sequence, List<Integer> nodes, int from, int to, int depth) {
        if (depth > sequence.length * sequence.length) {
            throw new IllegalStateException("The sequence matrix does not describe a route.");
        }
        int intermediate = sequence[from][to];
        if (intermediate == to) {
            if (nodes.isEmpty()) {
                nodes.add(from);
            }
            nodes.add(to);
            return;
        }
        expand(sequence, nodes, from, intermediate, depth + 1);
        expand(sequence, nodes, intermediate, to, depth + 1);
    }

    private static Iteration snapshot(int pivot, double[][] distance, int[][] sequence, List<Cell> improved) {
        int nodes = distance.length - 1;
        Double[][] shownDistance = new Double[nodes][nodes];
        Integer[][] shownSequence = new Integer[nodes][nodes];
        for (int row = 1; row <= nodes; row++) {
            for (int column = 1; column <= nodes; column++) {
                if (row == column) {
                    continue;
                }
                shownDistance[row - 1][column - 1] = distance[row][column] == Double.POSITIVE_INFINITY ? null : distance[row][column];
                shownSequence[row - 1][column - 1] = sequence[row][column];
            }
        }
        return new Iteration(pivot, shownDistance, shownSequence, List.copyOf(improved));
    }
}
