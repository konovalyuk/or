package com.mavan.or.models;

import java.util.Locale;

/**
 * Linear program: objective, structural constraints, and implicit nonnegativity.
 * The graphical solver uses the two-variable case from Taha, Chapter 2.
 */
public final class LinearProgram {

    public enum Sense { MAXIMIZE, MINIMIZE }

    public enum Relation { LE, GE, EQ }

    private final Sense sense;
    private final double[] objective;
    private final double[][] coefficients;
    private final Relation[] relations;
    private final double[] rhs;

    private LinearProgram(Sense sense, double[] objective, double[][] coefficients, Relation[] relations, double[] rhs) {
        this.sense = sense;
        this.objective = objective;
        this.coefficients = coefficients;
        this.relations = relations;
        this.rhs = rhs;
    }

    public static LinearProgram of(String sense, double[] objective, double[][] coefficients, String[] relations, double[] rhs) {
        if (sense == null || sense.isBlank())
            throw new IllegalArgumentException("Objective sense is required.");
        Sense parsedSense = switch (sense.trim().toUpperCase(Locale.ROOT)) {
            case "MAXIMIZE", "MAX" -> Sense.MAXIMIZE;
            case "MINIMIZE", "MIN" -> Sense.MINIMIZE;
            default -> throw new IllegalArgumentException("Objective sense must be MAXIMIZE or MINIMIZE.");
        };
        if (objective == null || objective.length == 0) {
            throw new IllegalArgumentException("Objective coefficients are required.");
        }
        if (coefficients == null || rhs == null || relations == null) {
            throw new IllegalArgumentException("Constraints are required.");
        }
        if (coefficients.length == 0)
            throw new IllegalArgumentException("At least one constraint is required.");
        if (coefficients.length != rhs.length || coefficients.length != relations.length) {
            throw new IllegalArgumentException("Each constraint needs coefficients, a relation, and a right-hand side.");
        }

        double[] parsedObjective = new double[objective.length];
        for (int j = 0; j < objective.length; j++)
            parsedObjective[j] = finite(objective[j], "Objective coefficient");

        double[][] rows = new double[coefficients.length][objective.length];
        Relation[] parsedRelations = new Relation[relations.length];
        double[] parsedRhs = rhs.clone();
        for (int i = 0; i < coefficients.length; i++) {
            if (coefficients[i] == null || coefficients[i].length != objective.length) {
                throw new IllegalArgumentException("Constraint " + (i + 1) + " must have one coefficient per variable.");
            }
            parsedRelations[i] = parseRelation(relations[i], i);
            parsedRhs[i] = finite(rhs[i], "Right-hand side");
            for (int j = 0; j < objective.length; j++)
                rows[i][j] = finite(coefficients[i][j], "Constraint coefficient");
        }
        return new LinearProgram(parsedSense, parsedObjective, rows, parsedRelations, parsedRhs);
    }

    private static Relation parseRelation(String relation, int index) {
        if (relation == null || relation.isBlank()) {
            throw new IllegalArgumentException("Constraint " + (index + 1) + " needs a relation.");
        }
        return switch (relation.trim().toUpperCase(Locale.ROOT)) {
            case "LE", "LEQ", "<=", "≤" -> Relation.LE;
            case "GE", "GEQ", ">=", "≥" -> Relation.GE;
            case "EQ", "=", "==" -> Relation.EQ;
            default -> throw new IllegalArgumentException("Constraint " + (index + 1) + " has an unknown relation.");
        };
    }

    private static double finite(double value, String label) {
        if (!Double.isFinite(value))
            throw new IllegalArgumentException(label + " must be a finite number.");
        return value;
    }

    public Sense sense() { return sense; }

    public int variables() { return objective.length; }

    public int constraints() { return rhs.length; }

    public double objective(int index) { return objective[index]; }

    public double coefficient(int row, int column) { return coefficients[row][column]; }

    public Relation relation(int row) { return relations[row]; }

    public double rhs(int row) { return rhs[row]; }
}
