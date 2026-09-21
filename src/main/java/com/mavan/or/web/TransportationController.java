package com.mavan.or.web;

import com.mavan.or.algorithms.transportation.datamodels.TransResult;
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
 * HTTP adapter for the transportation solver.
 */
@RestController
@RequestMapping("/api/transportation")
public class TransportationController {

    private final SolverService solver;

    public TransportationController(SolverService solver) {
        this.solver = solver;
    }

    @PostMapping("/solve")
    public TransResult solve(@RequestBody TransportationSolveRequest request) {
        return solver.transportationTask(request);
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public Map<String, String> invalidProblem(RuntimeException exception) {
        return Map.of("error", exception.getMessage());
    }
}
