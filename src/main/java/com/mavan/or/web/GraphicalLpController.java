package com.mavan.or.web;

import com.mavan.or.algorithms.lp.GraphicalResult;
import com.mavan.or.service.SolverService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * HTTP adapter for the two-variable graphical LP solver.
 */
@RestController
@RequestMapping("/api/lp")
public class GraphicalLpController {

    private final SolverService solver;

    public GraphicalLpController(SolverService solver) {
        this.solver = solver;
    }

    @PostMapping("/graphical")
    public GraphicalResult solve(@RequestBody GraphicalSolveRequest request) {
        return solver.graphicalTask(request);
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public Map<String, String> invalidProblem(RuntimeException exception) {
        return Map.of("error", exception.getMessage());
    }

}
