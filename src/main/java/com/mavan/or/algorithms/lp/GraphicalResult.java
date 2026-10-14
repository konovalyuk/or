package com.mavan.or.algorithms.lp;

import java.util.List;

/**
 * Geometry of a two-variable graphical solution: constraint lines, the feasible
 * region after each cut, and the optimal point, segment, or ray.
 */
public record GraphicalResult(
        String status,
        String sense,
        String objectiveLabel,
        double[] objective,
        double levelStart,
        double levelEnd,
        double xMax,
        double yMax,
        String optimumShape,
        List<ConstraintLine> constraints,
        List<Point> optimalPoints,
        List<Point> flatDirections,
        Double x1,
        Double x2,
        Double objectiveValue,
        List<Step> steps,
        String message
) {
    public record Point(double x, double y) {}

    public record ConstraintLine(int stepIndex, String label, Point lineFrom, Point lineTo) {}

    public record Step(
            int index,
            String kind,
            int constraintIndex,
            String title,
            List<Point> polygon,
            Point lineFrom,
            Point lineTo,
            boolean feasible
    ) {}
}
