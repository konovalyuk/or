package com.mavan.or.service;

import com.mavan.or.algorithms.lp.SimplexIteration;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class SolverService {

    public List<SimplexIteration> solve() {

        // Пока демонстрационные данные.
        // Здесь будет вызов настоящего SimplexSolver.

        return List.of(
                new SimplexIteration(0, BigDecimal.ZERO),
                new SimplexIteration(1, new BigDecimal("12")),
                new SimplexIteration(2, new BigDecimal("18")),
                new SimplexIteration(3, new BigDecimal("21"))
        );
    }
}
