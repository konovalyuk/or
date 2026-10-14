package com.mavan.or.web;

import com.mavan.or.algorithms.lp.SimplexResult;
import com.mavan.or.service.SolverService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * HTTP adapter for the simplex method and the list of starts that fit the current model.
 */
@RestController
@RequestMapping("/api/lp")
public class SimplexController {

    private final SolverService solver;

    public SimplexController(SolverService solver) {
        this.solver = solver;
    }

    @PostMapping("/simplex")
    public SimplexResult solve(@RequestBody SimplexSolveRequest request) {
        return solver.simplexTask(request);
    }

    @PostMapping("/simplex/methods")
    public List<String> methods(@RequestBody GraphicalSolveRequest request) {
        return solver.simplexMethods(request);
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public Map<String, String> invalidProblem(RuntimeException exception) {
        return Map.of("error", exception.getMessage());
    }
}
