package com.mavan.or.algorithms.transportation;

import com.mavan.or.algorithms.transportation.datamodels.TransResult;
import com.mavan.or.algorithms.transportation.datamodels.TransTable;
import com.mavan.or.algorithms.transportation.start.*;

import java.util.Locale;
import java.util.Scanner;

public class TransRunner {

    /**
     * Verification using the SunRay Transport example from Taha.
     * <p>
     * Expected optimal value:
     * <p>
     * 435
     * <p>
     * The unit transportation costs in the example are given
     * in hundreds of dollars.
     */
    public static void validateSunRayExample() {
        double[][] costs = {
                {10, 2, 20, 11},
                {12, 7, 9, 20},
                {4, 14, 16, 18}
        };

        double[] supply = {15, 25, 10};

        double[] demand = {5, 15, 15, 15};

        double expectedOptimalCost = 435.0;
        TransSolver solver = new TransSolver(
                new TransBasicSolution(new NorthwestCorner(), new LeastCost(), new Vogel()),
                new TransClosedLoop(),
                new TransPotentials());

        for (ETransBasicSolution method : ETransBasicSolution.values()) {
            var table = new TransTable(costs, supply, demand, new double[costs.length][costs[0].length], new boolean[costs.length][costs[0].length]);

            TransResult result = solver.solve(method, table);

            if (Math.abs(result.optimalCost() - expectedOptimalCost) > TransTable.EPS) {
                throw new AssertionError("SunRay validation failed for " + method + ": expected " + expectedOptimalCost + ", actual " + result.optimalCost());
            }

            TransValidation.validateSolution(result.optimalAllocation(), table);
            System.out.println("Optimal cost = " + result.optimalCost());
        }

        System.out.println("SunRay validation PASSED.");
        System.out.println("All three starting methods reach the optimum.");
    }


    /**
     * Run a detailed SunRay example in the console.
     * <p>
     * Useful for checking all intermediate tables.
     */
    public static void runSunRayExample(ETransBasicSolution method) {
        double[][] costs = {
                {10, 2, 20, 11},
                {12, 7, 9, 20},
                {4, 14, 16, 18}
        };

        double[] supply = {
                15, 25, 10
        };

        double[] demand = {
                5, 15, 15, 15
        };

        var table = new TransTable(costs, supply, demand, new double[costs.length][costs[0].length], new boolean[costs.length][costs[0].length]);
        ConsoleRenderer renderer = new ConsoleRenderer(table);

        var basicSolution = new TransBasicSolution(new NorthwestCorner(), new LeastCost(), new Vogel());
        TransSolver solver = new TransSolver(
                basicSolution,
                new TransClosedLoop(),
                new TransPotentials());

        basicSolution.buildInitialBasicSolution(method, table);
        solver.repairDegeneracy(table);

        renderer.printInitial(
                method,
                table.allocation(),
                table.basic(),
                solver.calculateTotalCost(table)
        );

        TransResult result = solver.solve(method, renderer::printState, table);

        renderer.printFinalSummary(result);
    }


    private static TransTable readProblemFromConsole(Scanner scanner) {
        System.out.println();
        System.out.println("================ TRANSPORTATION PROBLEM ================");
        System.out.print("Number of rows (sources / silos): ");
        int rows = scanner.nextInt();
        System.out.print("Number of columns (destinations / mills): ");
        int columns = scanner.nextInt();
        if (rows <= 0 || columns <= 0) {
            throw new IllegalArgumentException("Rows and columns must be positive.");
        }

        double[][] costs = new double[rows][columns];
        double[] supply = new double[rows];
        double[] demand = new double[columns];

        System.out.println();
        System.out.println("Enter the transportation cost matrix:");
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < columns; j++) {
                System.out.printf("c%d%d = ", i + 1, j + 1);
                costs[i][j] = scanner.nextDouble();
            }
        }

        System.out.println();
        System.out.println("Enter supply for each source:");
        for (int i = 0; i < rows; i++) {
            System.out.printf("Supply S%d = ", i + 1);
            supply[i] = scanner.nextDouble();
        }

        System.out.println();
        System.out.println("Enter demand for each destination:");

        for (int j = 0; j < columns; j++) {
            System.out.printf("Demand D%d = ", j + 1);
            demand[j] = scanner.nextDouble();
        }

        return new TransTable(costs, supply, demand, new double[costs.length][costs[0].length], new boolean[costs.length][costs[0].length]);
    }

    private static ETransBasicSolution readInitialMethod(Scanner scanner) {
        System.out.println();
        System.out.println("Select starting basic feasible solution:");
        System.out.println("1 - Northwest-corner method");
        System.out.println("2 - Least-cost method");
        System.out.println("3 - Vogel approximation method");
        System.out.print("Choice: ");

        int choice = scanner.nextInt();
        return switch (choice) {
            case 1 -> ETransBasicSolution.NORTHWEST_CORNER;
            case 2 -> ETransBasicSolution.LEAST_COST;
            case 3 -> ETransBasicSolution.VOGEL_APPROXIMATION;
            default -> throw new IllegalStateException("Invalid starting method.: " + choice);
        };
    }


    public static void main(String[] args) {
        Locale.setDefault(Locale.US);

        if (args.length > 0 && "validate".equalsIgnoreCase(args[0])) {
            validateSunRayExample();
            return;
        }

        if (args.length > 0 && "sunray".equalsIgnoreCase(args[0])) {
            runSunRayExample(ETransBasicSolution.NORTHWEST_CORNER);
            return;
        }

        Scanner scanner = new Scanner(System.in);

        try {
            TransTable table = readProblemFromConsole(scanner);
            ETransBasicSolution method = readInitialMethod(scanner);
            ConsoleRenderer renderer = new ConsoleRenderer(table);

            var basicSolution = new TransBasicSolution(new NorthwestCorner(), new LeastCost(), new Vogel());
            TransSolver solver = new TransSolver(
                    basicSolution,
                    new TransClosedLoop(),
                    new TransPotentials());
            /*
             * solve() already generates a complete sequence of states.
             *
             * The renderer receives every state immediately, which later
             * allows the same algorithm to feed a browser/WebSocket/REST
             * layer instead of the console.
             */
            TransResult result = solver.solve(method,
                    state -> {
                        if (state.iteration() == 0 && state.optimal()) {
                            renderer.printInitial(method, state.allocationAfter(), state.basicAfter(), state.totalCost());
                        } else if (state.iteration() == 0) {
                            renderer.printInitial(method, state.allocationAfter(), state.basicAfter(), state.totalCost());
                        } else {
                            renderer.printState(state);
                        }
                    },
                    table
            );

            renderer.printFinalSummary(result);
        } catch (Exception exception) {
            System.err.println();
            System.err.println("ERROR: " + exception.getMessage());
            exception.printStackTrace(System.err);
        } finally {
            scanner.close();
        }
    }
}
