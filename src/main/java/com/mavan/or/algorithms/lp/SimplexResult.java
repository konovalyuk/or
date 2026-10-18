package com.mavan.or.algorithms.lp;

import java.util.List;

/**
 * Tableau trace of a simplex solve, plus the optimal plan, algebraic sensitivity, and dual model when they exist.
 */
public record SimplexResult(
        String status,
        String method,
        String sense,
        Double objectiveValue,
        boolean degenerate,
        String message,
        List<VariableRow> variables,
        List<ConstraintRow> constraints,
        List<Iteration> iterations,
        Sensitivity sensitivity,
        DualProblem dual
) {
    public record VariableRow(String name, double value, double objectiveCoefficient, double contribution) {
    }

    public record ConstraintRow(int index, String relation, double rhs, double deviation, String kind) {
    }

    /**
     * A tableau entry m·M + constant. Constraint rows keep m at 0.
     */
    public record Cell(double m, double constant) {
    }

    public record Ratio(Double value, boolean selected) {
    }

    public record Iteration(
            int number,
            String phase,
            String objectiveLabel,
            List<String> columns,
            List<String> basic,
            List<Cell> objectiveRow,
            Cell objectiveRhs,
            List<List<Cell>> body,
            List<Cell> solution,
            Integer enteringColumn,
            Integer leavingRow,
            List<Ratio> rowRatios,
            List<Ratio> columnRatios,
            List<String> operations,
            boolean optimal,
            boolean degenerate,
            String note
    ) {
    }

    /**
     * A missing bound is infinity. {@code price} is the reduced cost or the dual price.
     */
    public record Range(double current, Double minimum, Double maximum, double price) {
    }

    public record Sensitivity(List<Range> variables, List<Range> constraints) {
    }

    /**
     * Dual built from Taha Table 4.2. {@code value} is the Method 2 price y = cB B^{-1}.
     * {@code sign} is NONNEGATIVE, NONPOSITIVE, or UNRESTRICTED.
     */
    public record DualVariable(String name, double objectiveCoefficient, double value, String sign) {
    }

    public record DualConstraint(String name, List<Double> coefficients, String relation, double rhs) {
    }

    public record DualProblem(String sense, List<DualVariable> variables, List<DualConstraint> constraints, double objectiveValue) {
    }
}
