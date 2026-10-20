package com.mavan.or.newpost.example;

import com.mavan.or.newpost.model.NewPostProblem;
import com.mavan.or.newpost.model.NewPostResult;
import com.mavan.or.newpost.model.VehicleType;
import com.mavan.or.newpost.optimization.NetworkOptimizer;

import java.util.List;

/** Fast exact regression tests for path splitting, transshipment and consolidation. */
public final class NewPostTinyExactTest {
    public static void main(String[] args) {
        NewPostProblem p = NewPostProblem.builder()
                .locations(List.of("A", "B", "C"))
                .demand(new double[][] {
                        {0, 9, 0},
                        {0, 0, 9},
                        {0, 0, 0}
                })
                .distance(new double[][] {
                        {0, 10, 2},
                        {11, 0, 2},
                        {2, 3, 0}
                })
                .terminalCapacity(new double[] {100, 100, 100})
                .terminalHandlingCost(new double[] {0, 0, 0})
                .vehicleTypes(List.of(
                        new VehicleType("20 t", 20, 1.0, 0),
                        new VehicleType("10 t", 10, 0.8, 0),
                        new VehicleType("5 t", 5, 0.5, 0)
                ))
                .loadingFactor(0.8)
                .directOnly(false)
                .build();

        NewPostResult result = new NetworkOptimizer().solve(p);
        if (result.getStatus() != NewPostResult.Status.OPTIMAL) throw new AssertionError(result.getMessage());
        if (Math.abs(result.getTotalTonnage() - 18.0) > 1e-6) throw new AssertionError("Tonnage");
        if (result.getShipments().stream().noneMatch(s -> s.getPath().size() > 2)) {
            throw new AssertionError("Expected a transshipment path");
        }
        System.out.println("Tiny exact New Post test PASSED");
        System.out.printf("Cost = %.4f, nodes = %d%n", result.getTotalCost(), result.getSearchNodes());
    }
}
