package com.mavan.or.algorithms.lp;

import com.mavan.or.algorithms.lp.SimplexResult.Cell;
import com.mavan.or.algorithms.lp.SimplexResult.ConstraintRow;
import com.mavan.or.algorithms.lp.SimplexResult.DualConstraint;
import com.mavan.or.algorithms.lp.SimplexResult.DualProblem;
import com.mavan.or.algorithms.lp.SimplexResult.DualVariable;
import com.mavan.or.algorithms.lp.SimplexResult.Iteration;
import com.mavan.or.algorithms.lp.SimplexResult.Range;
import com.mavan.or.algorithms.lp.SimplexResult.Ratio;
import com.mavan.or.algorithms.lp.SimplexResult.Sensitivity;
import com.mavan.or.algorithms.lp.SimplexResult.VariableRow;
import com.mavan.or.models.LinearProgram;
import com.mavan.or.models.LinearProgram.Relation;
import com.mavan.or.models.LinearProgram.Sense;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Primal simplex, the big-M method, the two-phase method, the dual simplex, and the generalized simplex.
 * <p>
 * Hamdy A. Taha, Operations Research: An Introduction, 10th ed.,
 * Chapter 3 (the simplex method and sensitivity), Section 4.4.1 (dual simplex),
 * and Section 4.4.2 (generalized simplex).
 * <p>
 * The objective row is stored as z − c·x = 0, so a maximization of 5x1 + 4x2
 * starts at −5, −4. An artificial penalty is the symbolic term m·M + constant,
 * not a large number. Iteration 1 is the starting basis, before any pivot.
 */
@Service
public class Simplex {

    public static final String ALL_SLACK = "ALL_SLACK";
    public static final String M_METHOD = "M_METHOD";
    public static final String TWO_PHASE = "TWO_PHASE";
    public static final String DUAL_SIMPLEX = "DUAL_SIMPLEX";
    public static final String GENERALIZED = "GENERALIZED";

    public static final String OPTIMAL = "OPTIMAL";
    public static final String ALTERNATIVE_OPTIMA = "ALTERNATIVE_OPTIMA";
    public static final String INFEASIBLE = "INFEASIBLE";
    public static final String UNBOUNDED = "UNBOUNDED";
    public static final String CYCLING = "CYCLING";

    private static final double EPS = 1e-8;
    private static final double SNAP = 1e-10;

    public SimplexResult solve(LinearProgram program, String method) {
        String chosen = method == null || method.isBlank()
                ? applicable(program).get(0)
                : canonical(method);
        if (!applicable(program).contains(chosen)) {
            throw new IllegalArgumentException("That simplex start does not apply to this model.");
        }
        String contradiction = contradiction(program);
        if (contradiction != null) {
            return bare(program, chosen, INFEASIBLE, contradiction);
        }

        Model model = build(program, chosen);
        List<Iteration> iterations = new ArrayList<>();
        int[] next = {1};

        if (model.rows() == 0) {
            return unconstrained(program, model, chosen, iterations, next);
        }
        if (TWO_PHASE.equals(chosen)) {
            return twoPhase(program, model, iterations, next);
        }
        if (DUAL_SIMPLEX.equals(chosen)) {
            installObjective(model, false);
            Outcome outcome = dualLoop(model, iterations, next);
            return finish(program, model, chosen, outcome, iterations);
        }
        if (GENERALIZED.equals(chosen)) {
            installObjective(model, false);
            if (dualLeaving(model) >= 0) {
                Outcome feasibility = restoreFeasibility(model, iterations, next);
                if (!OPTIMAL.equals(feasibility.status)) {
                    return finish(program, model, chosen, feasibility, iterations);
                }
            }
            Outcome outcome = primalLoop(model, iterations, next, "PRIMAL", !model.maximize);
            return finish(program, model, chosen, outcome, iterations);
        }
        installObjective(model, M_METHOD.equals(chosen));
        Outcome outcome = primalLoop(model, iterations, next, "PRIMAL", !model.maximize);
        if (OPTIMAL.equals(outcome.status) && (artificialValue(model) > EPS || Math.abs(model.zRhs.m) > EPS)) {
            outcome = new Outcome(INFEASIBLE, "An artificial variable is still positive, so the model is infeasible.");
        }
        return finish(program, model, chosen, outcome, iterations);
    }

    /** Methods whose starting basis exists for this model, in display order. */
    public static List<String> applicable(LinearProgram program) {
        if (allSlack(program)) {
            return List.of(ALL_SLACK);
        }
        List<String> methods = new ArrayList<>();
        if (dualStart(program)) {
            methods.add(DUAL_SIMPLEX);
        }
        methods.add(M_METHOD);
        methods.add(TWO_PHASE);
        if (generalizedStart(program)) {
            methods.add(GENERALIZED);
        }
        return List.copyOf(methods);
    }

    private SimplexResult twoPhase(LinearProgram program, Model model, List<Iteration> iterations, int[] next) {
        installPhaseOne(model);
        Outcome phaseOne = primalLoop(model, iterations, next, "PHASE_I", true);
        if (!OPTIMAL.equals(phaseOne.status)) {
            return finish(program, model, TWO_PHASE, phaseOne, iterations);
        }
        if (model.zRhs.c > EPS || Math.abs(model.zRhs.m) > EPS) {
            return bare(program, TWO_PHASE, INFEASIBLE,
                    "Phase I ends with a positive artificial objective, so the model is infeasible.", iterations);
        }
        exchangeArtificials(model, iterations, next);
        if (artificialValue(model) > EPS) {
            return bare(program, TWO_PHASE, INFEASIBLE,
                    "An artificial variable is still positive, so the model is infeasible.", iterations);
        }
        dropArtificialColumns(model);
        installOriginal(model);
        Outcome phaseTwo = primalLoop(model, iterations, next, "PHASE_II", !model.maximize);
        return finish(program, model, TWO_PHASE, phaseTwo, iterations);
    }

    private SimplexResult unconstrained(LinearProgram program, Model model, String method, List<Iteration> iterations, int[] next) {
        installObjective(model, false);
        int enter = entering(model, !model.maximize);
        if (enter >= 0) {
            iterations.add(snapshot(model, next[0]++, "PRIMAL", enter, null, false, false,
                    "No constraint can leave the basis, so the objective is unbounded."));
            return bare(program, method, UNBOUNDED,
                    "The entering column has no positive entry, so the objective is unbounded.", iterations);
        }
        iterations.add(snapshot(model, next[0]++, "PRIMAL", null, null, true, false, null));
        return finish(program, model, method, new Outcome(OPTIMAL, optimalMessage(false)), iterations);
    }

    private SimplexResult finish(LinearProgram program, Model model, String method, Outcome outcome, List<Iteration> iterations) {
        if (!OPTIMAL.equals(outcome.status)) {
            return bare(program, method, outcome.status, outcome.message, iterations);
        }
        View view = capture(program, model);
        Sensitivity sensitivity = sensitivity(program, model);
        DualProblem dual = dualProblem(program, model);
        String status = OPTIMAL;
        String message = optimalMessage(view.degenerate());
        int alternative = alternativeColumn(model);
        if (alternative >= 0) {
            int leave = leaving(model, alternative);
            int number = iterations.get(iterations.size() - 1).number();
            iterations.remove(iterations.size() - 1);
            if (leave < 0) {
                iterations.add(snapshot(model, number, "ALTERNATIVE", alternative, null, true, false,
                        "This nonbasic column has a zero objective coefficient and no positive entry. The same objective continues along a ray."));
                message = "A nonbasic variable has a zero objective coefficient, and its column has no positive entry. The same objective value continues along a ray.";
            } else {
                iterations.add(snapshot(model, number, "ALTERNATIVE", alternative, leave, false, false,
                        "This nonbasic column has a zero objective coefficient. Pivoting into it keeps the same objective value."));
                model.pivot(leave, alternative);
                iterations.add(snapshot(model, iterations.size() == 0 ? 1 : iterations.get(iterations.size() - 1).number() + 1,
                        "ALTERNATIVE", null, null, true, false, "Second optimal basis. The objective value is unchanged."));
                message = "A nonbasic variable has a zero objective coefficient, so a second basis has the same objective value.";
            }
            status = ALTERNATIVE_OPTIMA;
            if (view.degenerate()) {
                message = message + " The first optimal basis is degenerate.";
            }
        }
        return new SimplexResult(status, method, program.sense().name(), view.objective(), view.degenerate(), message,
                view.variables(), view.constraints(), List.copyOf(iterations), sensitivity, dual);
    }

    private Outcome primalLoop(Model model, List<Iteration> iterations, int[] next, String phase, boolean minimizing) {
        Set<String> seen = new HashSet<>();
        seen.add(model.basisKey());
        int cap = Math.max(200, 50 * (model.cols() + model.rows() + 1));
        while (iterations.size() < cap) {
            int enter = entering(model, minimizing);
            if (enter < 0) {
                int number = next[0]++;
                iterations.add(snapshot(model, number, phase, null, null, true, false, null));
                return new Outcome(OPTIMAL, optimalMessage(model.degenerate()), number);
            }
            int leave = leaving(model, enter);
            if (leave < 0) {
                iterations.add(snapshot(model, next[0]++, phase, enter, null, false, false,
                        "No nonnegative ratio. The entering column has no positive entry, so the objective is unbounded."));
                return new Outcome(UNBOUNDED, "The entering column has no positive entry, so the objective is unbounded.");
            }
            iterations.add(snapshot(model, next[0]++, phase, enter, leave, false, false, null));
            model.pivot(leave, enter);
            if (!seen.add(model.basisKey())) {
                iterations.add(snapshot(model, next[0]++, phase, null, null, false, false,
                        "This basis already appeared, so the simplex method is cycling."));
                return new Outcome(CYCLING, "A basis repeated, so the simplex method is cycling.");
            }
        }
        return new Outcome(CYCLING, "The iteration limit was reached before a stopping condition.");
    }

    private Outcome dualLoop(Model model, List<Iteration> iterations, int[] next) {
        Set<String> seen = new HashSet<>();
        seen.add(model.basisKey());
        int cap = Math.max(200, 50 * (model.cols() + model.rows() + 1));
        while (iterations.size() < cap) {
            int leave = dualLeaving(model);
            if (leave < 0) {
                int number = next[0]++;
                iterations.add(snapshot(model, number, "DUAL", null, null, true, true, null));
                return new Outcome(OPTIMAL, optimalMessage(model.degenerate()), number);
            }
            int enter = dualEntering(model, leave);
            if (enter < 0) {
                iterations.add(snapshot(model, next[0]++, "DUAL", null, leave, false, true,
                        "The pivot row has no negative coefficient, so the model has no feasible solution."));
                return new Outcome(INFEASIBLE, "The pivot row has no negative coefficient, so the model has no feasible solution.");
            }
            iterations.add(snapshot(model, next[0]++, "DUAL", enter, leave, false, true, null));
            model.pivot(leave, enter);
            if (!seen.add(model.basisKey())) {
                iterations.add(snapshot(model, next[0]++, "DUAL", null, null, false, true,
                        "This basis already appeared, so the simplex method is cycling."));
                return new Outcome(CYCLING, "A basis repeated, so the simplex method is cycling.");
            }
        }
        return new Outcome(CYCLING, "The iteration limit was reached before a stopping condition.");
    }

    /**
     * Section 4.4.2. The ordinary dual ratio is not used: the starting objective is not optimal,
     * so any negative pivot entry restores feasibility. The lowest index breaks ties.
     */
    private Outcome restoreFeasibility(Model model, List<Iteration> iterations, int[] next) {
        Set<String> seen = new HashSet<>();
        seen.add(model.basisKey());
        int cap = Math.max(200, 50 * (model.cols() + model.rows() + 1));
        while (iterations.size() < cap) {
            int leave = dualLeaving(model);
            if (leave < 0) {
                return new Outcome(OPTIMAL, "Feasibility restored.");
            }
            int enter = lowestNegative(model, leave);
            if (enter < 0) {
                iterations.add(snapshot(model, next[0]++, "GENERALIZED", null, leave, false, false,
                        "The pivot row has no negative coefficient, so the model has no feasible solution."));
                return new Outcome(INFEASIBLE, "The pivot row has no negative coefficient, so the model has no feasible solution.");
            }
            iterations.add(snapshot(model, next[0]++, "GENERALIZED", enter, leave, false, false,
                    "Feasibility is restored first. The entering variable is the lowest-index negative entry in the pivot row."));
            model.pivot(leave, enter);
            if (!seen.add(model.basisKey())) {
                iterations.add(snapshot(model, next[0]++, "GENERALIZED", null, null, false, false,
                        "This basis already appeared, so the simplex method is cycling."));
                return new Outcome(CYCLING, "A basis repeated, so the simplex method is cycling.");
            }
        }
        return new Outcome(CYCLING, "The iteration limit was reached before a stopping condition.");
    }

    private static int lowestNegative(Model model, int leave) {
        boolean[] basic = model.basicFlags();
        for (int column = 0; column < model.cols(); column++) {
            if (!basic[column] && model.A[leave][column] < -EPS) {
                return column;
            }
        }
        return -1;
    }

    private void exchangeArtificials(Model model, List<Iteration> iterations, int[] next) {
        boolean moved = true;
        while (moved) {
            moved = false;
            for (int row = 0; row < model.rows(); row++) {
                if (!model.artificial[model.basis[row]] || Math.abs(model.b[row]) > EPS) {
                    continue;
                }
                int column = firstReplacement(model, row);
                if (column < 0) {
                    model.dropRow(row);
                } else {
                    iterations.add(snapshot(model, next[0]++, "PHASE_I", column, row, false, false,
                            "The basic artificial variable is zero. It leaves on a nonzero pivot so the original variables can take the basis."));
                    model.pivot(row, column);
                }
                moved = true;
                break;
            }
        }
    }

    private static int firstReplacement(Model model, int row) {
        for (int column = 0; column < model.cols(); column++) {
            if (!model.artificial[column] && Math.abs(model.A[row][column]) > EPS) {
                return column;
            }
        }
        return -1;
    }

    private static void dropArtificialColumns(Model model) {
        boolean[] keep = new boolean[model.cols()];
        for (int column = 0; column < keep.length; column++) {
            keep[column] = !model.artificial[column];
        }
        model.retainColumns(keep);
    }

    private static int entering(Model model, boolean minimizing) {
        int chosen = -1;
        Term best = null;
        boolean[] basic = model.basicFlags();
        for (int column = 0; column < model.cols(); column++) {
            if (basic[column]) {
                continue;
            }
            Term coefficient = model.z[column];
            if (minimizing) {
                if (coefficient.positive() && (chosen < 0 || coefficient.compare(best) > 0)) {
                    chosen = column;
                    best = coefficient;
                }
            } else if (coefficient.negative() && (chosen < 0 || coefficient.compare(best) < 0)) {
                chosen = column;
                best = coefficient;
            }
        }
        return chosen;
    }

    private static int leaving(Model model, int enter) {
        int chosen = -1;
        double best = 0;
        for (int row = 0; row < model.rows(); row++) {
            if (model.A[row][enter] <= EPS) {
                continue;
            }
            double ratio = model.b[row] / model.A[row][enter];
            if (ratio < -EPS) {
                continue;
            }
            if (chosen < 0 || ratio < best - EPS) {
                chosen = row;
                best = ratio;
            }
        }
        return chosen;
    }

    private static int dualLeaving(Model model) {
        int chosen = -1;
        double most = 0;
        for (int row = 0; row < model.rows(); row++) {
            if (model.b[row] < -EPS && (chosen < 0 || model.b[row] < most - EPS)) {
                chosen = row;
                most = model.b[row];
            }
        }
        return chosen;
    }

    private static int dualEntering(Model model, int leave) {
        int chosen = -1;
        double best = 0;
        boolean[] basic = model.basicFlags();
        for (int column = 0; column < model.cols(); column++) {
            if (basic[column] || model.A[leave][column] >= -EPS) {
                continue;
            }
            double ratio = Math.abs(model.z[column].c / model.A[leave][column]);
            if (chosen < 0 || ratio < best - EPS) {
                chosen = column;
                best = ratio;
            }
        }
        return chosen;
    }

    private static int alternativeColumn(Model model) {
        boolean[] basic = model.basicFlags();
        for (int column = 0; column < model.cols(); column++) {
            if (!basic[column] && !model.artificial[column] && model.z[column].zero()) {
                return column;
            }
        }
        return -1;
    }

    private static double artificialValue(Model model) {
        double worst = 0;
        for (int row = 0; row < model.rows(); row++) {
            if (model.artificial[model.basis[row]]) {
                worst = Math.max(worst, model.b[row]);
            }
        }
        return worst;
    }

    private Iteration snapshot(Model model, int number, String phase, Integer enter, Integer leave, boolean optimal, boolean dual, String note) {
        List<Cell> objective = new ArrayList<>();
        for (Term coefficient : model.z) {
            objective.add(cell(coefficient));
        }
        List<String> basic = new ArrayList<>();
        List<List<Cell>> body = new ArrayList<>();
        List<Cell> solution = new ArrayList<>();
        for (int row = 0; row < model.rows(); row++) {
            basic.add(model.name[model.basis[row]]);
            List<Cell> line = new ArrayList<>();
            for (int column = 0; column < model.cols(); column++) {
                line.add(cell(model.A[row][column]));
            }
            body.add(line);
            solution.add(cell(model.b[row]));
        }
        List<Ratio> rowRatios = null;
        List<Ratio> columnRatios = null;
        List<String> operations = List.of();
        if ("GENERALIZED".equals(phase)) {
            rowRatios = null;
            columnRatios = null;
        } else if (dual && leave != null) {
            columnRatios = dualRatios(model, leave, enter);
        } else if (!dual && enter != null) {
            rowRatios = primalRatios(model, enter, leave);
        }
        if (enter != null && leave != null) {
            operations = operations(model, phase, enter, leave);
        }
        String label = "PHASE_I".equals(phase) ? "w (min)" : (model.maximize ? "z (max)" : "z (min)");
        return new Iteration(number, phase, label, List.copyOf(Arrays.asList(model.name)), basic, objective, cell(model.zRhs),
                body, solution, enter, leave, rowRatios, columnRatios, operations, optimal, model.degenerate(), note);
    }

    private static List<Ratio> primalRatios(Model model, int enter, Integer leave) {
        List<Ratio> ratios = new ArrayList<>();
        for (int row = 0; row < model.rows(); row++) {
            if (model.A[row][enter] > EPS) {
                double ratio = model.b[row] / model.A[row][enter];
                if (ratio >= -EPS) {
                    ratios.add(new Ratio(Math.max(0, ratio), leave != null && row == leave));
                    continue;
                }
            }
            ratios.add(new Ratio(null, false));
        }
        return ratios;
    }

    private static List<Ratio> dualRatios(Model model, int leave, Integer enter) {
        List<Ratio> ratios = new ArrayList<>();
        boolean[] basic = model.basicFlags();
        for (int column = 0; column < model.cols(); column++) {
            if (!basic[column] && model.A[leave][column] < -EPS) {
                ratios.add(new Ratio(Math.abs(model.z[column].c / model.A[leave][column]), enter != null && column == enter));
            } else {
                ratios.add(new Ratio(null, false));
            }
        }
        return ratios;
    }

    private static List<String> operations(Model model, String phase, int enter, int leave) {
        String entering = model.name[enter];
        String leaving = model.name[model.basis[leave]];
        String objective = "PHASE_I".equals(phase) ? "w" : "z";
        List<String> lines = new ArrayList<>();
        lines.add("New " + entering + " row = " + leaving + " row / " + num(model.A[leave][enter]));
        lines.add("New " + objective + " row = " + objective + " row − (" + term(model.z[enter]) + ") × new " + entering + " row");
        for (int row = 0; row < model.rows(); row++) {
            if (row == leave) {
                continue;
            }
            String current = model.name[model.basis[row]];
            lines.add("New " + current + " row = " + current + " row − (" + num(model.A[row][enter]) + ") × new " + entering + " row");
        }
        return List.copyOf(lines);
    }

    private View capture(LinearProgram program, Model model) {
        double[] values = new double[program.variables()];
        for (int row = 0; row < model.rows(); row++) {
            int column = model.basis[row];
            if (model.structural[column]) {
                values[model.structuralIndex[column]] = model.b[row];
            }
        }
        List<VariableRow> variables = new ArrayList<>();
        for (int index = 0; index < values.length; index++) {
            double coefficient = program.objective(index);
            variables.add(new VariableRow("x" + (index + 1), values[index], coefficient, coefficient * values[index]));
        }
        List<ConstraintRow> constraints = new ArrayList<>();
        for (int index = 0; index < program.constraints(); index++) {
            double activity = 0;
            for (int column = 0; column < program.variables(); column++) {
                activity += program.coefficient(index, column) * values[column];
            }
            double rhs = program.rhs(index);
            Relation relation = program.relation(index);
            double deviation = switch (relation) {
                case LE -> rhs - activity;
                case GE, EQ -> activity - rhs;
            };
            String kind = switch (relation) {
                case LE -> "SLACK";
                case GE -> "SURPLUS";
                case EQ -> "EQUATION";
            };
            constraints.add(new ConstraintRow(index + 1, relation.name(), rhs, deviation, kind));
        }
        return new View(model.zRhs.c, model.degenerate(), variables, constraints);
    }

    private DualProblem dualProblem(LinearProgram program, Model model) {
        double[] prices = shadowPrices(program, model);
        if (prices == null) {
            return null;
        }
        boolean maximize = program.sense() == Sense.MAXIMIZE;
        List<DualVariable> variables = new ArrayList<>();
        double objective = 0;
        for (int index = 0; index < program.constraints(); index++) {
            String sign = switch (program.relation(index)) {
                case EQ -> "UNRESTRICTED";
                case LE -> maximize ? "NONNEGATIVE" : "NONPOSITIVE";
                case GE -> maximize ? "NONPOSITIVE" : "NONNEGATIVE";
            };
            variables.add(new DualVariable("y" + (index + 1), program.rhs(index), prices[index], sign));
            objective += program.rhs(index) * prices[index];
        }
        List<DualConstraint> constraints = new ArrayList<>();
        String relation = maximize ? "GE" : "LE";
        for (int variable = 0; variable < program.variables(); variable++) {
            List<Double> coefficients = new ArrayList<>();
            for (int row = 0; row < program.constraints(); row++) {
                coefficients.add(snap(program.coefficient(row, variable)));
            }
            constraints.add(new DualConstraint("x" + (variable + 1), List.copyOf(coefficients), relation, program.objective(variable)));
        }
        return new DualProblem(maximize ? "MINIMIZE" : "MAXIMIZE", List.copyOf(variables), List.copyOf(constraints), snap(objective));
    }

    /** Method 2 prices, with the same row-flip factor the sensitivity dual price already uses. */
    private double[] shadowPrices(LinearProgram program, Model model) {
        int constraints = program.constraints();
        double[] shadow = new double[constraints];
        int rows = model.rows();
        if (rows == 0) {
            return shadow;
        }
        double[][] inverse = invert(basisMatrix(model));
        if (inverse == null) {
            return null;
        }
        double[] basicCost = new double[rows];
        for (int row = 0; row < rows; row++) {
            int column = model.basis[row];
            if (model.structural[column]) {
                basicCost[row] = model.cost[model.structuralIndex[column]];
            }
        }
        double[] prices = new double[rows];
        for (int column = 0; column < rows; column++) {
            for (int row = 0; row < rows; row++) {
                prices[column] += basicCost[row] * inverse[row][column];
            }
        }
        for (int index = 0; index < constraints; index++) {
            double dual = 0;
            boolean represented = false;
            for (int row = 0; row < rows; row++) {
                if (model.origin[row] == index) {
                    represented = true;
                    dual += prices[row] * model.factor[row];
                }
            }
            shadow[index] = represented ? snap(dual) : 0;
        }
        return shadow;
    }

    private Sensitivity sensitivity(LinearProgram program, Model model) {
        int variables = program.variables();
        int constraints = program.constraints();
        Range[] variableRanges = new Range[variables];
        Range[] constraintRanges = new Range[constraints];
        int rows = model.rows();
        double[][] inverse = rows == 0 ? new double[0][0] : invert(basisMatrix(model));
        if (rows > 0 && inverse == null) {
            return null;
        }
        boolean[] basic = model.basicFlags();
        int[] basicRow = new int[model.cols()];
        Arrays.fill(basicRow, -1);
        for (int row = 0; row < rows; row++) {
            basicRow[model.basis[row]] = row;
        }
        for (int index = 0; index < variables; index++) {
            int column = structuralColumn(model, index);
            double current = program.objective(index);
            if (column < 0) {
                variableRanges[index] = new Range(current, null, null, 0);
                continue;
            }
            if (basic[column]) {
                variableRanges[index] = objectiveRange(model, basicRow[column], current, 0);
            } else {
                variableRanges[index] = nonbasicRange(model, column, current);
            }
        }
        boolean[] represented = new boolean[constraints];
        for (int row = 0; row < rows; row++) {
            if (model.origin[row] >= 0) {
                represented[model.origin[row]] = true;
            }
        }
        for (int index = 0; index < constraints; index++) {
            if (!represented[index]) {
                constraintRanges[index] = tautologyRange(program, index);
                continue;
            }
            double[] direction = new double[rows];
            for (int row = 0; row < rows; row++) {
                if (model.origin[row] == index) {
                    direction[row] += model.factor[row];
                }
            }
            double[] step = multiply(inverse, direction);
            double lower = Double.NEGATIVE_INFINITY;
            double upper = Double.POSITIVE_INFINITY;
            for (int row = 0; row < rows; row++) {
                if (Math.abs(step[row]) <= EPS) {
                    continue;
                }
                double bound = -model.b[row] / step[row];
                if (step[row] > 0) {
                    lower = Math.max(lower, bound);
                } else {
                    upper = Math.min(upper, bound);
                }
            }
            double rhs = program.rhs(index);
            double[] basicCost = new double[rows];
            for (int row = 0; row < rows; row++) {
                int column = model.basis[row];
                if (model.structural[column]) {
                    basicCost[row] = model.cost[model.structuralIndex[column]];
                }
            }
            double[] prices = new double[rows];
            for (int column = 0; column < rows; column++) {
                for (int row = 0; row < rows; row++) {
                    prices[column] += basicCost[row] * inverse[row][column];
                }
            }
            double dual = 0;
            for (int row = 0; row < rows; row++) {
                dual += prices[row] * direction[row];
            }
            constraintRanges[index] = new Range(rhs, bound(rhs + lower), bound(rhs + upper), snap(dual));
        }
        return new Sensitivity(List.of(variableRanges), List.of(constraintRanges));
    }

    private static Range objectiveRange(Model model, int row, double current, double reduced) {
        double lower = Double.NEGATIVE_INFINITY;
        double upper = Double.POSITIVE_INFINITY;
        boolean[] basic = model.basicFlags();
        for (int column = 0; column < model.cols(); column++) {
            if (basic[column] || model.artificial[column] || Math.abs(model.z[column].m) > EPS) {
                continue;
            }
            double entry = model.A[row][column];
            if (Math.abs(entry) <= EPS) {
                continue;
            }
            double limit = -model.z[column].c / entry;
            if (model.maximize) {
                if (entry > 0) {
                    lower = Math.max(lower, limit);
                } else {
                    upper = Math.min(upper, limit);
                }
            } else if (entry > 0) {
                upper = Math.min(upper, limit);
            } else {
                lower = Math.max(lower, limit);
            }
        }
        return new Range(current, bound(current + lower), bound(current + upper), reduced);
    }

    private static Range nonbasicRange(Model model, int column, double current) {
        Term reduced = model.z[column];
        if (Math.abs(reduced.m) > EPS) {
            return new Range(current, null, null, reduced.c);
        }
        if (model.maximize) {
            return new Range(current, null, bound(current + reduced.c), snap(reduced.c));
        }
        return new Range(current, bound(current + reduced.c), null, snap(reduced.c));
    }

    private static Range tautologyRange(LinearProgram program, int index) {
        double rhs = program.rhs(index);
        return switch (program.relation(index)) {
            case LE -> new Range(rhs, 0.0, null, 0);
            case GE -> new Range(rhs, null, 0.0, 0);
            case EQ -> new Range(rhs, 0.0, 0.0, 0);
        };
    }

    private static int structuralColumn(Model model, int index) {
        for (int column = 0; column < model.cols(); column++) {
            if (model.structural[column] && model.structuralIndex[column] == index) {
                return column;
            }
        }
        return -1;
    }

    private static double[][] basisMatrix(Model model) {
        int rows = model.rows();
        double[][] basis = new double[rows][rows];
        for (int column = 0; column < rows; column++) {
            for (int row = 0; row < rows; row++) {
                basis[row][column] = model.standardA[row][model.basis[column]];
            }
        }
        return basis;
    }

    private static double[][] invert(double[][] matrix) {
        int size = matrix.length;
        double[][] work = new double[size][size * 2];
        for (int row = 0; row < size; row++) {
            System.arraycopy(matrix[row], 0, work[row], 0, size);
            work[row][size + row] = 1;
        }
        for (int column = 0; column < size; column++) {
            int pivot = column;
            for (int row = column + 1; row < size; row++) {
                if (Math.abs(work[row][column]) > Math.abs(work[pivot][column])) {
                    pivot = row;
                }
            }
            if (Math.abs(work[pivot][column]) < SNAP) {
                return null;
            }
            double[] saved = work[column];
            work[column] = work[pivot];
            work[pivot] = saved;
            double scale = work[column][column];
            for (int index = 0; index < work[column].length; index++) {
                work[column][index] /= scale;
            }
            for (int row = 0; row < size; row++) {
                if (row == column) {
                    continue;
                }
                double factor = work[row][column];
                for (int index = 0; index < work[row].length; index++) {
                    work[row][index] -= factor * work[column][index];
                }
            }
        }
        double[][] inverse = new double[size][size];
        for (int row = 0; row < size; row++) {
            System.arraycopy(work[row], size, inverse[row], 0, size);
        }
        return inverse;
    }

    private static double[] multiply(double[][] matrix, double[] vector) {
        double[] product = new double[vector.length];
        for (int row = 0; row < matrix.length; row++) {
            for (int column = 0; column < vector.length; column++) {
                product[row] += matrix[row][column] * vector[column];
            }
        }
        return product;
    }

    private static void installObjective(Model model, boolean bigM) {
        model.z = new Term[model.cols()];
        for (int column = 0; column < model.cols(); column++) {
            if (model.structural[column]) {
                model.z[column] = new Term(0, -model.cost[model.structuralIndex[column]]);
            } else if (bigM && model.artificial[column]) {
                model.z[column] = new Term(model.maximize ? 1 : -1, 0);
            } else {
                model.z[column] = new Term(0, 0);
            }
        }
        model.zRhs = new Term(0, 0);
        if (!bigM) {
            return;
        }
        for (int row = 0; row < model.rows(); row++) {
            if (model.artificial[model.basis[row]]) {
                eliminate(model, row);
            }
        }
    }

    private static void installPhaseOne(Model model) {
        model.z = new Term[model.cols()];
        for (int column = 0; column < model.cols(); column++) {
            model.z[column] = model.artificial[column] ? new Term(0, -1) : new Term(0, 0);
        }
        model.zRhs = new Term(0, 0);
        for (int row = 0; row < model.rows(); row++) {
            if (model.artificial[model.basis[row]]) {
                eliminate(model, row);
            }
        }
    }

    private static void installOriginal(Model model) {
        installObjective(model, false);
        for (int row = 0; row < model.rows(); row++) {
            eliminate(model, row);
        }
    }

    private static void eliminate(Model model, int row) {
        Term coefficient = model.z[model.basis[row]];
        if (coefficient.zero()) {
            return;
        }
        for (int column = 0; column < model.cols(); column++) {
            model.z[column] = model.z[column].minus(coefficient.times(model.A[row][column]));
        }
        model.zRhs = model.zRhs.minus(coefficient.times(model.b[row]));
    }

    private static Model build(LinearProgram program, String method) {
        List<Draft> drafts = new ArrayList<>();
        if (DUAL_SIMPLEX.equals(method) || GENERALIZED.equals(method)) {
            for (int index = 0; index < program.constraints(); index++) {
                if (allZero(program, index)) {
                    continue;
                }
                double[] coefficients = coefficients(program, index);
                double rhs = program.rhs(index);
                switch (program.relation(index)) {
                    case LE -> drafts.add(new Draft(coefficients, rhs, index, 1));
                    case GE -> drafts.add(new Draft(negate(coefficients), -rhs, index, -1));
                    case EQ -> {
                        drafts.add(new Draft(coefficients, rhs, index, 1));
                        drafts.add(new Draft(negate(coefficients), -rhs, index, -1));
                    }
                }
            }
            return assemble(program, drafts, true);
        }
        for (int index = 0; index < program.constraints(); index++) {
            if (allZero(program, index)) {
                continue;
            }
            double[] coefficients = coefficients(program, index);
            double rhs = program.rhs(index);
            Relation relation = program.relation(index);
            double factor = 1;
            if (rhs < -EPS) {
                coefficients = negate(coefficients);
                rhs = -rhs;
                relation = flip(relation);
                factor = -1;
            }
            drafts.add(new Draft(coefficients, rhs, index, factor, relation));
        }
        return assemble(program, drafts, false);
    }

    private static Model assemble(LinearProgram program, List<Draft> drafts, boolean slackOnly) {
        int structuralCount = program.variables();
        int extras = 0;
        for (Draft draft : drafts) {
            extras += slackOnly || draft.relation == Relation.LE || draft.relation == Relation.EQ ? 1 : 2;
        }
        int columns = structuralCount + extras;
        int rows = drafts.size();
        Model model = new Model(program.sense() == Sense.MAXIMIZE, structuralCount, columns, rows);
        for (int index = 0; index < structuralCount; index++) {
            model.name[index] = "x" + (index + 1);
            model.structural[index] = true;
            model.structuralIndex[index] = index;
            model.cost[index] = program.objective(index);
        }
        int column = structuralCount;
        int slack = structuralCount + 1;
        int artificial = 1;
        for (int row = 0; row < rows; row++) {
            Draft draft = drafts.get(row);
            System.arraycopy(draft.coefficients, 0, model.A[row], 0, structuralCount);
            System.arraycopy(draft.coefficients, 0, model.standardA[row], 0, structuralCount);
            model.b[row] = draft.rhs;
            model.standardB[row] = draft.rhs;
            model.origin[row] = draft.origin;
            model.factor[row] = draft.factor;
            if (slackOnly || draft.relation == Relation.LE) {
                model.A[row][column] = 1;
                model.standardA[row][column] = 1;
                model.basis[row] = column;
                model.name[column] = "sx" + slack++;
                column++;
            } else if (draft.relation == Relation.GE) {
                model.A[row][column] = -1;
                model.standardA[row][column] = -1;
                model.name[column] = "sx" + slack++;
                column++;
                model.A[row][column] = 1;
                model.standardA[row][column] = 1;
                model.basis[row] = column;
                model.artificial[column] = true;
                model.name[column] = "R" + artificial++;
                column++;
            } else {
                model.A[row][column] = 1;
                model.standardA[row][column] = 1;
                model.basis[row] = column;
                model.artificial[column] = true;
                model.name[column] = "R" + artificial++;
                column++;
            }
        }
        model.snap();
        return model;
    }

    private static boolean allSlack(LinearProgram program) {
        for (int index = 0; index < program.constraints(); index++) {
            if (allZero(program, index)) {
                if (inconsistent(program, index)) {
                    return false;
                }
                continue;
            }
            Relation relation = program.relation(index);
            double rhs = program.rhs(index);
            boolean ready = (relation == Relation.LE && rhs >= -EPS) || (relation == Relation.GE && rhs < -EPS);
            if (!ready) {
                return false;
            }
        }
        return true;
    }

    private static boolean dualStart(LinearProgram program) {
        return objectiveReady(program) && negativeSlackStart(program);
    }

    private static boolean generalizedStart(LinearProgram program) {
        return !objectiveReady(program) && negativeSlackStart(program);
    }

    private static boolean negativeSlackStart(LinearProgram program) {
        boolean negative = false;
        for (int index = 0; index < program.constraints(); index++) {
            if (allZero(program, index)) {
                if (inconsistent(program, index)) {
                    negative = true;
                }
                continue;
            }
            Relation relation = program.relation(index);
            double rhs = program.rhs(index);
            if ((relation == Relation.LE && rhs < -EPS) || (relation == Relation.GE && rhs > EPS) || (relation == Relation.EQ && Math.abs(rhs) > EPS)) {
                negative = true;
            }
        }
        return negative;
    }

    private static boolean objectiveReady(LinearProgram program) {
        for (int index = 0; index < program.variables(); index++) {
            double coefficient = program.objective(index);
            if (program.sense() == Sense.MAXIMIZE && coefficient > EPS) {
                return false;
            }
            if (program.sense() == Sense.MINIMIZE && coefficient < -EPS) {
                return false;
            }
        }
        return true;
    }

    private static String contradiction(LinearProgram program) {
        for (int index = 0; index < program.constraints(); index++) {
            if (allZero(program, index) && inconsistent(program, index)) {
                return "Constraint " + (index + 1) + " cannot be satisfied.";
            }
        }
        return null;
    }

    private static boolean inconsistent(LinearProgram program, int index) {
        double rhs = program.rhs(index);
        return switch (program.relation(index)) {
            case LE -> rhs < -EPS;
            case GE -> rhs > EPS;
            case EQ -> Math.abs(rhs) > EPS;
        };
    }

    private static boolean allZero(LinearProgram program, int row) {
        for (int column = 0; column < program.variables(); column++) {
            if (Math.abs(program.coefficient(row, column)) > EPS) {
                return false;
            }
        }
        return true;
    }

    private static double[] coefficients(LinearProgram program, int row) {
        double[] values = new double[program.variables()];
        for (int column = 0; column < values.length; column++) {
            values[column] = program.coefficient(row, column);
        }
        return values;
    }

    private static double[] negate(double[] values) {
        double[] copy = values.clone();
        for (int index = 0; index < copy.length; index++) {
            copy[index] = -copy[index];
        }
        return copy;
    }

    private static Relation flip(Relation relation) {
        return switch (relation) {
            case LE -> Relation.GE;
            case GE -> Relation.LE;
            case EQ -> Relation.EQ;
        };
    }

    private static String canonical(String method) {
        return switch (method.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_')) {
            case "ALL_SLACK", "ALLSLACK", "SLACK" -> ALL_SLACK;
            case "M_METHOD", "M", "BIG_M", "BIGM" -> M_METHOD;
            case "TWO_PHASE", "TWOPHASE" -> TWO_PHASE;
            case "DUAL_SIMPLEX", "DUAL" -> DUAL_SIMPLEX;
            case "GENERALIZED", "GENERALIZED_SIMPLEX" -> GENERALIZED;
            default -> throw new IllegalArgumentException("Unknown simplex method.");
        };
    }

    private static SimplexResult bare(LinearProgram program, String method, String status, String message) {
        return bare(program, method, status, message, List.of());
    }

    private static SimplexResult bare(LinearProgram program, String method, String status, String message, List<Iteration> iterations) {
        return new SimplexResult(status, method, program.sense().name(), null, false, message,
                List.of(), List.of(), List.copyOf(iterations), null, null);
    }

    private static String optimalMessage(boolean degenerate) {
        return degenerate
                ? "An optimal basic feasible solution was found. The basis is degenerate."
                : "An optimal basic feasible solution was found.";
    }

    private static Cell cell(Term term) {
        return new Cell(term.m, term.c);
    }

    private static Cell cell(double value) {
        return new Cell(0, snap(value));
    }

    private static Double bound(double value) {
        return Double.isInfinite(value) ? null : snap(value);
    }

    private static double snap(double value) {
        return Math.abs(value) < SNAP ? 0 : value;
    }

    private static String num(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private static String term(Term value) {
        if (Math.abs(value.m) < EPS) {
            return num(value.c);
        }
        String penalty = num(value.m) + "M";
        if (Math.abs(value.c) < EPS) {
            return penalty;
        }
        return value.c > 0 ? penalty + "+" + num(value.c) : penalty + num(value.c);
    }

    private record Draft(double[] coefficients, double rhs, int origin, double factor, Relation relation) {
        private Draft(double[] coefficients, double rhs, int origin, double factor) {
            this(coefficients, rhs, origin, factor, Relation.LE);
        }
    }

    private record View(double objective, boolean degenerate, List<VariableRow> variables, List<ConstraintRow> constraints) {
    }

    private static final class Outcome {
        private final String status;
        private final String message;
        private int number;

        private Outcome(String status, String message) {
            this(status, message, 0);
        }

        private Outcome(String status, String message, int number) {
            this.status = status;
            this.message = message;
            this.number = number;
        }
    }

    private static final class Term {
        private final double m;
        private final double c;

        private Term(double m, double c) {
            this.m = Math.abs(m) < SNAP ? 0 : m;
            this.c = Math.abs(c) < SNAP ? 0 : c;
        }

        private Term minus(Term other) {
            return new Term(m - other.m, c - other.c);
        }

        private Term times(double factor) {
            return new Term(m * factor, c * factor);
        }

        private int sign() {
            if (m > EPS) {
                return 1;
            }
            if (m < -EPS) {
                return -1;
            }
            if (c > EPS) {
                return 1;
            }
            if (c < -EPS) {
                return -1;
            }
            return 0;
        }

        private boolean positive() {
            return sign() > 0;
        }

        private boolean negative() {
            return sign() < 0;
        }

        private boolean zero() {
            return sign() == 0;
        }

        private int compare(Term other) {
            if (m < other.m - EPS) {
                return -1;
            }
            if (m > other.m + EPS) {
                return 1;
            }
            if (c < other.c - EPS) {
                return -1;
            }
            if (c > other.c + EPS) {
                return 1;
            }
            return 0;
        }
    }

    private static final class Model {
        private final boolean maximize;
        private final double[] cost;
        private String[] name;
        private boolean[] structural;
        private boolean[] artificial;
        private int[] structuralIndex;
        private double[][] A;
        private double[] b;
        private double[][] standardA;
        private double[] standardB;
        private Term[] z;
        private Term zRhs;
        private int[] basis;
        private int[] origin;
        private double[] factor;

        private Model(boolean maximize, int structuralCount, int columns, int rows) {
            this.maximize = maximize;
            cost = new double[structuralCount];
            name = new String[columns];
            structural = new boolean[columns];
            artificial = new boolean[columns];
            structuralIndex = new int[columns];
            Arrays.fill(structuralIndex, -1);
            A = new double[rows][columns];
            b = new double[rows];
            standardA = new double[rows][columns];
            standardB = new double[rows];
            basis = new int[rows];
            origin = new int[rows];
            factor = new double[rows];
            z = new Term[columns];
            Arrays.fill(z, new Term(0, 0));
            zRhs = new Term(0, 0);
        }

        private int rows() {
            return b.length;
        }

        private int cols() {
            return name.length;
        }

        private void pivot(int row, int column) {
            double pivot = A[row][column];
            for (int index = 0; index < cols(); index++) {
                A[row][index] /= pivot;
            }
            b[row] /= pivot;
            snapRow(row);
            Term coefficient = z[column];
            for (int index = 0; index < cols(); index++) {
                z[index] = z[index].minus(coefficient.times(A[row][index]));
            }
            zRhs = zRhs.minus(coefficient.times(b[row]));
            for (int other = 0; other < rows(); other++) {
                if (other == row) {
                    continue;
                }
                double scale = A[other][column];
                if (Math.abs(scale) < SNAP) {
                    A[other][column] = 0;
                    continue;
                }
                for (int index = 0; index < cols(); index++) {
                    A[other][index] -= scale * A[row][index];
                }
                b[other] -= scale * b[row];
                snapRow(other);
            }
            basis[row] = column;
            snapRow(row);
        }

        private void dropRow(int row) {
            A = removeRow(A, row);
            standardA = removeRow(standardA, row);
            b = remove(b, row);
            standardB = remove(standardB, row);
            basis = remove(basis, row);
            origin = remove(origin, row);
            factor = remove(factor, row);
        }

        private void retainColumns(boolean[] keep) {
            int count = 0;
            for (boolean kept : keep) {
                if (kept) {
                    count++;
                }
            }
            int[] map = new int[cols()];
            Arrays.fill(map, -1);
            String[] names = new String[count];
            boolean[] structuralCopy = new boolean[count];
            boolean[] artificialCopy = new boolean[count];
            int[] indexCopy = new int[count];
            Term[] objective = new Term[count];
            int position = 0;
            for (int column = 0; column < cols(); column++) {
                if (!keep[column]) {
                    continue;
                }
                map[column] = position;
                names[position] = name[column];
                structuralCopy[position] = structural[column];
                artificialCopy[position] = artificial[column];
                indexCopy[position] = structuralIndex[column];
                objective[position] = z[column];
                position++;
            }
            double[][] body = new double[rows()][count];
            double[][] standard = new double[rows()][count];
            for (int row = 0; row < rows(); row++) {
                position = 0;
                for (int column = 0; column < cols(); column++) {
                    if (!keep[column]) {
                        continue;
                    }
                    body[row][position] = A[row][column];
                    standard[row][position] = standardA[row][column];
                    position++;
                }
                if (map[basis[row]] < 0) {
                    throw new IllegalStateException("A basic column was removed.");
                }
                basis[row] = map[basis[row]];
            }
            name = names;
            structural = structuralCopy;
            artificial = artificialCopy;
            structuralIndex = indexCopy;
            z = objective;
            A = body;
            standardA = standard;
        }

        private void snap() {
            for (int row = 0; row < rows(); row++) {
                snapRow(row);
            }
        }

        private void snapRow(int row) {
            for (int column = 0; column < cols(); column++) {
                if (Math.abs(A[row][column]) < SNAP) {
                    A[row][column] = 0;
                }
            }
            if (Math.abs(b[row]) < SNAP) {
                b[row] = 0;
            }
        }

        private boolean[] basicFlags() {
            boolean[] flags = new boolean[cols()];
            for (int basicColumn : basis) {
                flags[basicColumn] = true;
            }
            return flags;
        }

        private String basisKey() {
            int[] copy = basis.clone();
            Arrays.sort(copy);
            return Arrays.toString(copy);
        }

        private boolean degenerate() {
            for (double value : b) {
                if (Math.abs(value) <= EPS) {
                    return true;
                }
            }
            return false;
        }

        private static double[][] removeRow(double[][] matrix, int row) {
            double[][] copy = new double[matrix.length - 1][];
            for (int index = 0, target = 0; index < matrix.length; index++) {
                if (index != row) {
                    copy[target++] = matrix[index];
                }
            }
            return copy;
        }

        private static double[] remove(double[] values, int index) {
            double[] copy = new double[values.length - 1];
            System.arraycopy(values, 0, copy, 0, index);
            System.arraycopy(values, index + 1, copy, index, values.length - index - 1);
            return copy;
        }

        private static int[] remove(int[] values, int index) {
            int[] copy = new int[values.length - 1];
            System.arraycopy(values, 0, copy, 0, index);
            System.arraycopy(values, index + 1, copy, index, values.length - index - 1);
            return copy;
        }
    }
}
