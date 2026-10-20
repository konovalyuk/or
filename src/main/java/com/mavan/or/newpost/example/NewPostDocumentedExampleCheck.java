package com.mavan.or.newpost.example;

import com.mavan.or.newpost.model.NewPostResult;
import com.mavan.or.newpost.optimization.NetworkOptimizer;

/**
 * Loads the complete New Post document example and checks feasibility/cost
 * decomposition without waiting for a full proof of global optimality.
 */
public final class NewPostDocumentedExampleCheck {
    public static void main(String[] args) {
        long nodes = args.length == 0 ? 1L : Long.parseLong(args[0]);
        NewPostResult result = new NetworkOptimizer().solve(NewPostExample.documentedExample(), nodes);
        if (result.getStatus() == NewPostResult.Status.INFEASIBLE) {
            throw new AssertionError(result.getMessage());
        }
        if (Math.abs(result.getTotalTonnage() - 56.0) > 1e-6) {
            throw new AssertionError("Expected 56 t, got " + result.getTotalTonnage());
        }
        if (Math.abs(result.getTotalCost() - result.getTransportCost() - result.getTerminalCost()) > 1e-6) {
            throw new AssertionError("Cost decomposition failed");
        }
        System.out.println("Documented New Post data model check PASSED");
        System.out.println("Status = " + result.getStatus());
        System.out.printf("Feasible cost = %.2f AED%n", result.getTotalCost());
        System.out.println("MILP nodes explored = " + result.getSearchNodes());
    }
}
