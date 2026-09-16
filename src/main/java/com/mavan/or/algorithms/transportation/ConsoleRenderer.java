package com.mavan.or.algorithms.transportation;

import com.mavan.or.algorithms.transportation.datamodels.TransResult;
import com.mavan.or.algorithms.transportation.datamodels.TransState;
import com.mavan.or.algorithms.transportation.datamodels.TransTable;
import com.mavan.or.algorithms.transportation.datamodels.TransTableCell;
import com.mavan.or.algorithms.transportation.start.ETransBasicSolution;

import java.util.List;
import java.util.Locale;

public class ConsoleRenderer {

    private final TransTable table;

    public ConsoleRenderer(TransTable table) {
        this.table = table;
    }

    public void printInitial(ETransBasicSolution method, double[][] allocation, boolean[][] basic, double cost) {
        System.out.println();
        System.out.println("============================================================");
        System.out.println("INITIAL BASIC FEASIBLE SOLUTION: " + method);
        System.out.println("============================================================");

        printTransportationTable(allocation, basic);

        System.out.printf(Locale.US, "Initial cost = %.10f%n", cost);
    }

    public void printState(TransState state) {
        if (state.optimal()) {
            System.out.println();
            System.out.println("============================================================");
            System.out.println("OPTIMALITY CONDITION SATISFIED");
            System.out.println("============================================================");

            printPotentials(state);
            printEvaluation(state);
            printTransportationTable(state.allocation(), state.basic());

            System.out.printf(Locale.US, "Optimal cost = %.10f%n", state.totalCost());

            return;
        }

        System.out.println();
        System.out.println("============================================================");
        System.out.println("ITERATION " + state.iteration());
        System.out.println("============================================================");

        System.out.printf(Locale.US, "Entering variable: %s%n", state.entering());

        System.out.printf(Locale.US, "Leaving variable : %s%n", state.leaving());

        System.out.printf(Locale.US, "Theta            : %.10f%n", state.theta());

        printCycle(state.cycle());
        printPotentials(state);
        printEvaluation(state);

        System.out.println();
        System.out.println("Transportation table after pivot:");

        printTransportationTable(state.allocation(), state.basic());

        System.out.printf(Locale.US, "Cost = %.10f%n", state.totalCost());
    }

    public void printFinalSummary(TransResult result) {
        System.out.println();
        System.out.println("============================================================");
        System.out.println("OPTIMUM SOLUTION SUMMARY");
        System.out.println("============================================================");

        System.out.printf(Locale.US, "%-12s %-12s %-18s%n", "From", "To", "Number of truckloads");

        System.out.println("------------------------------------------------------------");

        for (int i = 0; i < result.optimalAllocation().length; i++) {
            for (int j = 0; j < result.optimalAllocation()[i].length; j++) {
                double quantity = result.optimalAllocation()[i][j];
                if (quantity > TransTable.EPS) {
                    System.out.printf(Locale.US, "%-12s %-12s %-18.10f%n", "Source " + (i + 1), "Destination " + (j + 1), quantity);
                }
            }
        }

        System.out.println("------------------------------------------------------------");

        System.out.printf(Locale.US, "Optimal cost = %.10f%n", result.optimalCost());
    }

    private void printCycle(List<TransTableCell> cycle) {
        System.out.print("Closed loop: ");

        for (int i = 0; i < cycle.size(); i++) {
            TransTableCell cell = cycle.get(i);
            if (i > 0) {
                System.out.print(i % 2 == 0 ? " + " : " - ");
            }
            System.out.print(cell);
        }

        System.out.println();
    }

    private void printPotentials(TransState state) {
        System.out.println();
        System.out.println("Transportation potentials:");

        System.out.print("u = [");

        for (int i = 0; i < state.u().length; i++) {
            if (i > 0) {
                System.out.print(", ");
            }
            System.out.printf(Locale.US, "%.4f", state.u()[i]);
        }
        System.out.println("]");

        System.out.print("v = [");
        for (int j = 0; j < state.v().length; j++) {
            if (j > 0) {
                System.out.print(", ");
            }
            System.out.printf(Locale.US, "%.4f", state.v()[j]);
        }
        System.out.println("]");
    }

    private void printEvaluation(TransState state) {
        System.out.println();
        System.out.println("Evaluation R(i,j) = u(i) + v(j) - c(i,j)");
        System.out.println("Positive value for a nonbasic cell => improving variable");

        System.out.printf("%10s", "");

        for (int j = 0; j < table.getColumns(); j++) {
            System.out.printf(Locale.US, "%14s", "D" + (j + 1));
        }

        System.out.println();

        for (int i = 0; i < table.getRows(); i++) {
            System.out.printf("%10s", "S" + (i + 1));

            for (int j = 0; j < table.getColumns(); j++) {
                String marker = state.basic()[i][j] ? "*" : "";
                System.out.printf(Locale.US, "%13.4f%s", state.evaluation()[i][j], marker);
            }
            System.out.println();
        }

        System.out.println("* = basic cell");
    }

    private void printTransportationTable(double[][] allocation, boolean[][] basic) {
        System.out.println();

        /*
         * Header
         */
        System.out.printf("%10s", "");

        for (int j = 0; j < table.getColumns(); j++) {
            System.out.printf("%14s", "D" + (j + 1));
        }

        System.out.printf("%14s%n", "Supply");

        /*
         * Source rows
         */
        for (int i = 0; i < table.getRows(); i++) {
            System.out.printf("%10s", "S" + (i + 1));

            for (int j = 0; j < table.getColumns(); j++) {
                String marker = basic[i][j] ? "*" : "";

                System.out.printf(Locale.US, "%13.4f%s", allocation[i][j], marker);
            }

            System.out.printf(Locale.US, "%14.4f%n", table.supply()[i]);
        }

        /*
         * Demand row
         */
        System.out.printf("%10s", "Demand");

        for (int j = 0; j < table.getColumns(); j++) {
            System.out.printf(Locale.US, "%14.4f", table.demand()[j]);
        }

        System.out.println();
        System.out.println("* = basic variable");
    }
}

