package com.mavan.or.web;

import com.mavan.or.algorithms.transportation.TransSolver;
import com.mavan.or.algorithms.transportation.datamodels.TransResult;
import com.mavan.or.algorithms.transportation.datamodels.TransTable;
import com.mavan.or.algorithms.transportation.start.ETransBasicSolution;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** HTTP adapter for the transportation solver. */
@RestController
@RequestMapping("/api/transportation")
public class TransportationController {

    private final TransSolver solver;

    public TransportationController(TransSolver solver) {
        this.solver = solver;
    }

    @PostMapping("/solve")
    public TransResult solve(@RequestBody TransportationSolveRequest request) {
        ETransBasicSolution method = request.initialMethod() == null
                ? ETransBasicSolution.VOGEL_APPROXIMATION
                : request.initialMethod();
        int rows = request.costs().length;
        int columns = request.costs()[0].length;
        TransTable table = new TransTable(
                request.costs(), request.supply(), request.demand(),
                new double[rows][columns], new boolean[rows][columns]
        );
        return solver.solve(method, table);
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public Map<String, String> invalidProblem(RuntimeException exception) {
        return Map.of("error", exception.getMessage());
    }
}
