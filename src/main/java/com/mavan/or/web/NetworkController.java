package com.mavan.or.web;

import com.mavan.or.algorithms.network.cpm.CriticalPath;
import com.mavan.or.algorithms.network.maximumflow.FordFulkerson;
import com.mavan.or.algorithms.network.minimumspanningtree.MinimalSpanningTree;
import com.mavan.or.algorithms.network.shortestpath.Dijkstra;
import com.mavan.or.algorithms.network.shortestpath.Floyd;
import com.mavan.or.service.SolverService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/network")
public class NetworkController {

    private final SolverService solver;

    public NetworkController(SolverService solver) {
        this.solver = solver;
    }

    @PostMapping("/spanning-tree")
    public MinimalSpanningTree.Result spanningTree(@RequestBody NetworkSolveRequest request) {
        return solver.spanningTreeTask(request);
    }

    @PostMapping("/shortest-route/dijkstra")
    public Dijkstra.Result dijkstra(@RequestBody NetworkSolveRequest request) {
        return solver.dijkstraTask(request);
    }

    @PostMapping("/shortest-route/floyd")
    public Floyd.Result floyd(@RequestBody NetworkSolveRequest request) {
        return solver.floydTask(request);
    }

    @PostMapping("/max-flow")
    public FordFulkerson.Result maximalFlow(@RequestBody NetworkSolveRequest request) {
        return solver.maximalFlowTask(request);
    }

    @PostMapping("/cpm")
    public CriticalPath.Result criticalPath(@RequestBody NetworkSolveRequest request) {
        return solver.criticalPathTask(request);
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public Map<String, String> invalidNetwork(RuntimeException exception) {
        return Map.of("error", exception.getMessage());
    }
}
