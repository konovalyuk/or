package com.mavan.or.service;

import com.mavan.or.algorithms.lp.GraphicalLpSolver;
import com.mavan.or.algorithms.lp.GraphicalResult;
import com.mavan.or.algorithms.lp.Simplex;
import com.mavan.or.algorithms.lp.SimplexResult;
import com.mavan.or.algorithms.network.cpm.CriticalPath;
import com.mavan.or.algorithms.network.maximumflow.FordFulkerson;
import com.mavan.or.algorithms.network.minimumspanningtree.MinimalSpanningTree;
import com.mavan.or.algorithms.network.shortestpath.Dijkstra;
import com.mavan.or.algorithms.network.shortestpath.Floyd;
import com.mavan.or.algorithms.transportation.TransSolver;
import com.mavan.or.algorithms.transportation.datamodels.TransResult;
import com.mavan.or.algorithms.transportation.datamodels.TransTable;
import com.mavan.or.algorithms.transportation.start.ETransBasicSolution;
import com.mavan.or.algorithms.transportation.TransValidation;
import com.mavan.or.models.LinearProgram;
import com.mavan.or.models.NetworkProblem;
import com.mavan.or.web.GraphicalSolveRequest;
import com.mavan.or.web.NetworkSolveRequest;
import com.mavan.or.web.SimplexSolveRequest;
import com.mavan.or.web.TransportationSolveRequest;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class SolverService {

    private final TransSolver transSolver;
    private final GraphicalLpSolver graphicalLpSolver;
    private final Simplex simplex;
    private final MinimalSpanningTree spanningTree;
    private final Dijkstra dijkstra;
    private final Floyd floyd;
    private final FordFulkerson maximalFlow;
    private final CriticalPath criticalPath;

    public SolverService(TransSolver transSolver, GraphicalLpSolver graphicalLpSolver, Simplex simplex,
                         MinimalSpanningTree spanningTree, Dijkstra dijkstra, Floyd floyd,
                         FordFulkerson maximalFlow, CriticalPath criticalPath) {
        this.transSolver = transSolver;
        this.graphicalLpSolver = graphicalLpSolver;
        this.simplex = simplex;
        this.spanningTree = spanningTree;
        this.dijkstra = dijkstra;
        this.floyd = floyd;
        this.maximalFlow = maximalFlow;
        this.criticalPath = criticalPath;
    }

    public TransResult transportationTask(TransportationSolveRequest request) {
        if (request == null) throw new IllegalArgumentException("Request body is required.");
        TransValidation.validateInput(request.costs(), request.supply(), request.demand());
        ETransBasicSolution method = request.initialMethod() == null
                ? ETransBasicSolution.VOGEL_APPROXIMATION
                : request.initialMethod();
        Balance balance = balance(request.costs(), request.supply(), request.demand());
        int rows = balance.costs.length;
        int columns = balance.costs[0].length;
        TransTable table = new TransTable(
                balance.costs, balance.supply, balance.demand,
                new double[rows][columns], new boolean[rows][columns]
        );
        return transSolver.solve(method, null, table, balance.sourceLabels, balance.destinationLabels,
                balance.dummyAdded, balance.dummyType, balance.dummyIndex, balance.dummyQuantity);
    }

    public GraphicalResult graphicalTask(GraphicalSolveRequest request) {
        if (request == null)
            throw new IllegalArgumentException("Request body is required.");
        var program = LinearProgram.of(request.sense(), request.objective(), request.coefficients(), request.relations(), request.rhs());
        return graphicalLpSolver.solve(program);
    }

    public List<String> simplexMethods(GraphicalSolveRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required.");
        }
        return Simplex.applicable(program(request.sense(), request.objective(), request.coefficients(), request.relations(), request.rhs()));
    }

    public MinimalSpanningTree.Result spanningTreeTask(NetworkSolveRequest request) {
        NetworkProblem problem = network(request);
        return spanningTree.solve(problem, request.start() == null ? 1 : request.start());
    }

    public Dijkstra.Result dijkstraTask(NetworkSolveRequest request) {
        return dijkstra.solve(network(request), request.start() == null ? 1 : request.start());
    }

    public Floyd.Result floydTask(NetworkSolveRequest request) {
        return floyd.solve(network(request));
    }

    public FordFulkerson.Result maximalFlowTask(NetworkSolveRequest request) {
        return maximalFlow.solve(network(request));
    }

    public CriticalPath.Result criticalPathTask(NetworkSolveRequest request) {
        NetworkProblem problem = network(request);
        if (request.optimistic() == null) {
            return criticalPath.solve(problem);
        }
        return criticalPath.solve(problem, request.optimistic(), request.likely(), request.pessimistic(), request.scheduled());
    }

    private static NetworkProblem network(NetworkSolveRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required.");
        }
        return NetworkProblem.of(request.nodes(), request.from(), request.to(), request.value(), request.symbol());
    }

    public SimplexResult simplexTask(SimplexSolveRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required.");
        }
        var program = program(request.sense(), request.objective(), request.coefficients(), request.relations(), request.rhs());
        return simplex.solve(program, request.method());
    }

    private static LinearProgram program(String sense, double[] objective, double[][] coefficients, String[] relations, double[] rhs) {
        return LinearProgram.of(sense, objective, coefficients, relations, rhs);
    }

    private Balance balance(double[][] originalCosts, double[] originalSupply, double[] originalDemand) {
        double totalSupply = Arrays.stream(originalSupply).sum();
        double totalDemand = Arrays.stream(originalDemand).sum();
        double difference = totalSupply - totalDemand;
        if (Math.abs(difference) <= TransTable.EPS) {
            return new Balance(copy(originalCosts), originalSupply.clone(), originalDemand.clone(),
                    labels("Source", originalCosts.length), labels("Destination", originalCosts[0].length),
                    false, "NONE", -1, 0.0);
        }

        if (difference > 0) {
            double[][] costs = new double[originalCosts.length][originalCosts[0].length + 1];
            for (int i = 0; i < originalCosts.length; i++) System.arraycopy(originalCosts[i], 0, costs[i], 0, originalCosts[i].length);
            double[] demand = Arrays.copyOf(originalDemand, originalDemand.length + 1);
            demand[demand.length - 1] = difference;
            return new Balance(costs, originalSupply.clone(), demand,
                    labels("Source", originalCosts.length), append(labels("Destination", originalDemand.length), "Dummy Destination"),
                    true, "DESTINATION", demand.length - 1, difference);
        }

        double[][] costs = new double[originalCosts.length + 1][originalCosts[0].length];
        for (int i = 0; i < originalCosts.length; i++) System.arraycopy(originalCosts[i], 0, costs[i], 0, originalCosts[i].length);
        double[] supply = Arrays.copyOf(originalSupply, originalSupply.length + 1);
        supply[supply.length - 1] = -difference;
        return new Balance(costs, supply, originalDemand.clone(),
                append(labels("Source", originalSupply.length), "Dummy Source"), labels("Destination", originalDemand.length),
                true, "SOURCE", supply.length - 1, -difference);
    }

    private static double[][] copy(double[][] source) {
        double[][] result = new double[source.length][];
        for (int i = 0; i < source.length; i++) result[i] = source[i].clone();
        return result;
    }

    private static String[] labels(String prefix, int size) {
        String[] result = new String[size];
        for (int i = 0; i < size; i++) result[i] = prefix + " " + (i + 1);
        return result;
    }

    private static String[] append(String[] values, String value) {
        String[] result = Arrays.copyOf(values, values.length + 1);
        result[result.length - 1] = value;
        return result;
    }

    private record Balance(double[][] costs, double[] supply, double[] demand,
                           String[] sourceLabels, String[] destinationLabels,
                           boolean dummyAdded, String dummyType, int dummyIndex, double dummyQuantity) {
        private Balance {
            for (int i = 0; i < costs.length; i++) {
                if (costs[i].length > 0 && i >= 0) {
                    // Newly allocated dummy cells are already zero; this copy keeps the input immutable.
                    costs[i] = costs[i].clone();
                }
            }
            supply = supply.clone();
            demand = demand.clone();
            sourceLabels = sourceLabels.clone();
            destinationLabels = destinationLabels.clone();
        }
    }

}
