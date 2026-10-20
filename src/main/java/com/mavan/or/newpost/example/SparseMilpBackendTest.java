package com.mavan.or.newpost.example;

import com.mavan.or.newpost.optimization.SparsePublicMilpEngine;

import java.util.List;

/** Regression tests for the sparse MILP backend itself. */
public final class SparseMilpBackendTest {
    public static void main(String[] args) {
        testFiniteUpperBoundMakesUnboundedRelaxationBounded();
        testZeroUpperBound();
        testIntegerBranching();
        System.out.println("Sparse MILP backend tests PASSED");
    }

    private static void testFiniteUpperBoundMakesUnboundedRelaxationBounded() {
        // min -x subject to 0 <= x <= 5.
        // The LP is unbounded until the finite bound is materialized lazily.
        var result = SparsePublicMilpEngine.solve(
                1,
                new double[]{-1.0},
                new boolean[]{false},
                new double[]{5.0},
                List.of(),
                null,
                1000);
        require(result.feasible(), "Expected feasible bounded LP");
        require(result.provenOptimal(), "Expected optimal LP");
        require(Math.abs(result.objective() + 5.0) < 1e-7,
                "Expected objective -5, got " + result.objective());
    }

    private static void testZeroUpperBound() {
        // min x subject to x <= 0, x >= 0.
        var result = SparsePublicMilpEngine.solve(
                1,
                new double[]{1.0},
                new boolean[]{false},
                new double[]{0.0},
                List.of(),
                null,
                1000);
        require(result.feasible(), "Expected feasible zero-bound model");
        require(Math.abs(result.objective()) < 1e-7,
                "Expected objective 0, got " + result.objective());
    }

    private static void testIntegerBranching() {
        // min x subject to x >= 3.5, x integer => x = 4.
        var result = SparsePublicMilpEngine.solve(
                1,
                new double[]{1.0},
                new boolean[]{true},
                new double[]{10.0},
                List.of(new SparsePublicMilpEngine.Constraint(
                        new int[]{0}, new double[]{-1.0}, -3.5)),
                null,
                10_000);
        require(result.feasible(), "Expected feasible integer model");
        require(result.provenOptimal(), "Expected proven integer optimum");
        require(Math.abs(result.objective() - 4.0) < 1e-7,
                "Expected objective 4, got " + result.objective());
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
