package com.mavan.or.algorithms.lp;

import com.mavan.or.algorithms.lp.GraphicalResult.ConstraintLine;
import com.mavan.or.algorithms.lp.GraphicalResult.Point;
import com.mavan.or.algorithms.lp.GraphicalResult.Step;
import com.mavan.or.models.LinearProgram;
import com.mavan.or.models.LinearProgram.Relation;
import com.mavan.or.models.LinearProgram.Sense;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Graphical solution of a two-variable linear program.
 * <p>
 * Hamdy A. Taha, Operations Research: An Introduction, 10th ed., Chapter 2,
 * Modeling with Linear Programming, Sections 2.1–2.2.
 * <p>
 * The feasible region is the intersection of the constraint half-planes with the
 * first quadrant. A bounded linear objective attains its optimum at a corner.
 * The search reports a point, a segment, an unbounded optimal edge, an empty
 * region, or an objective that improves without bound.
 */
@Service
public class GraphicalLpSolver {

    private static final double EPS = 1e-8;

    public GraphicalResult solve(LinearProgram program) {
        if (program.variables() != 2) {
            throw new IllegalArgumentException("The graphical method solves models with exactly two variables.");
        }
        List<Line> lines = new ArrayList<>();
        lines.add(new Line(1, 0, 0));
        lines.add(new Line(0, 1, 0));

        List<Half> halves = new ArrayList<>();
        halves.add(new Half(-1, 0, 0));
        halves.add(new Half(0, -1, 0));

        for (int i = 0; i < program.constraints(); i++) {
            double a1 = program.coefficient(i, 0);
            double a2 = program.coefficient(i, 1);
            double rhs = program.rhs(i);
            if (Math.hypot(a1, a2) < EPS)
                continue;
            lines.add(new Line(a1, a2, rhs));
            switch (program.relation(i)) {
                case LE -> halves.add(new Half(a1, a2, rhs));
                case GE -> halves.add(new Half(-a1, -a2, -rhs));
                case EQ -> {
                    halves.add(new Half(a1, a2, rhs));
                    halves.add(new Half(-a1, -a2, -rhs));
                }
            }
        }

        List<Point> vertices = vertices(lines, program);
        double[] window = window(program, vertices);
        double viewX = window[0];
        double viewY = window[1];

        List<Point> polygon = rectangle(viewX, viewY);
        List<Step> steps = new ArrayList<>();
        List<ConstraintLine> constraintLines = new ArrayList<>();
        steps.add(step(0, "NONNEGATIVE", -1, "x1 ≥ 0, x2 ≥ 0", polygon, null, null, true));

        for (int i = 0; i < program.constraints(); i++) {
            double a1 = program.coefficient(i, 0);
            double a2 = program.coefficient(i, 1);
            double rhs = program.rhs(i);
            Relation relation = program.relation(i);

            String label = constraintLabel(i, a1, a2, relation, rhs);
            Point[] segment = Math.hypot(a1, a2) < EPS ? null : segmentInBox(a1, a2, rhs, viewX, viewY);
            if (Math.hypot(a1, a2) < EPS) {
                if (!zeroRowHolds(relation, rhs))
                    polygon = List.of();
            } else if (relation == Relation.EQ) {
                polygon = cutByLine(polygon, a1, a2, rhs);
            } else if (relation == Relation.LE) {
                polygon = clip(polygon, a1, a2, rhs);
            } else {
                polygon = clip(polygon, -a1, -a2, -rhs);
            }
            Point from = segment == null ? null : segment[0];
            Point to = segment == null ? null : segment[1];
            int stepIndex = i + 1;
            constraintLines.add(new ConstraintLine(stepIndex, label, from, to));
            steps.add(step(stepIndex, "CONSTRAINT", i, label, polygon, from, to, !polygon.isEmpty()));
        }

        double c1 = program.objective(0);
        double c2 = program.objective(1);
        String objectiveLabel = (program.sense() == Sense.MAXIMIZE ? "Maximize" : "Minimize") + " z = " + expression(c1, c2);
        if (vertices.isEmpty() || polygon.isEmpty()) {
            steps.add(step(steps.size(), "OBJECTIVE", -1, objectiveLabel, polygon, null, null, false));
            return result("INFEASIBLE", "NONE", program, objectiveLabel, 0, 0, viewX, viewY,
                    constraintLines, List.of(), List.of(), null, null, null, steps,
                    "The constraints have no point in common.");
        }

        List<Point> rays = recessionDirections(halves);
        List<Point> improving = new ArrayList<>();
        List<Point> flat = new ArrayList<>();
        for (Point ray : rays) {
            double slope = c1 * ray.x() + c2 * ray.y();
            if (program.sense() == Sense.MAXIMIZE ? slope > 1e-6 : slope < -1e-6)
                improving.add(ray);
            else if (Math.abs(slope) <= 1e-6)
                flat.add(ray);
        }
        if (!improving.isEmpty()) {
            double exit = program.sense() == Sense.MAXIMIZE ? boxExtreme(c1, c2, viewX, viewY, true)
                    : boxExtreme(c1, c2, viewX, viewY, false);
            Point[] leaving = segmentInBox(c1, c2, exit, viewX, viewY);
            steps.add(step(steps.size(), "OBJECTIVE", -1, objectiveLabel, polygon,
                    leaving == null ? null : leaving[0], leaving == null ? null : leaving[1], true));
            return result("UNBOUNDED", "NONE", program, objectiveLabel, 0, exit, viewX, viewY,
                    constraintLines, List.of(), List.of(), null, null, null, steps,
                    "The objective is unbounded on the feasible region.");
        }

        double best = program.sense() == Sense.MAXIMIZE ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        for (Point vertex : vertices) {
            double value = c1 * vertex.x() + c2 * vertex.y();
            best = program.sense() == Sense.MAXIMIZE ? Math.max(best, value) : Math.min(best, value);
        }
        double tolerance = 1e-6 * (1 + Math.abs(best));
        List<Point> optimal = new ArrayList<>();
        for (Point vertex : vertices) {
            if (Math.abs(c1 * vertex.x() + c2 * vertex.y() - best) <= tolerance)
                optimal.add(vertex);
        }
        optimal.sort(Comparator.comparingDouble(Point::x).thenComparingDouble(Point::y));
        boolean entire = optimal.size() == vertices.size() && vertices.size() > 1;
        String shape;
        List<Point> reported;
        String message;
        Double x1 = null;
        Double x2 = null;
        if (optimal.size() == 1 && flat.isEmpty()) {
            shape = "POINT";
            reported = optimal;
            x1 = optimal.get(0).x();
            x2 = optimal.get(0).y();
            message = "The optimum is a corner of the feasible region.";
        } else if (!flat.isEmpty() && !entire) {
            shape = "RAY";
            reported = optimal;
            message = "The same objective value continues along an unbounded edge.";
        } else if (entire || (optimal.size() > 2 && flat.isEmpty() && spansArea(optimal))) {
            shape = "REGION";
            reported = orderAroundCentroid(optimal);
            message = "Every feasible solution is optimal.";
        } else {
            shape = "SEGMENT";
            reported = endpoints(optimal);
            message = "Any point on the segment joining the optimal corners is optimal.";
        }
        Point[] level = segmentInBox(c1, c2, best, viewX, viewY);
        steps.add(step(steps.size(), "OBJECTIVE", -1, objectiveLabel, polygon, level == null ? null : level[0], level == null ? null : level[1], true));
        return result(shape.equals("POINT") ? "OPTIMAL" : "ALTERNATIVE_OPTIMA", shape, program, objectiveLabel,
                0, best, viewX, viewY, constraintLines, reported, flat, x1, x2, best, steps, message);
    }

    private static GraphicalResult result(String status, String shape, LinearProgram program, String objectiveLabel,
                                          double levelStart, double levelEnd, double viewX, double viewY,
                                          List<ConstraintLine> constraints, List<Point> optimal, List<Point> flat,
                                          Double x1, Double x2, Double objectiveValue, List<Step> steps, String message) {
        return new GraphicalResult(status, program.sense().name(), objectiveLabel,
                new double[]{program.objective(0), program.objective(1)}, levelStart, levelEnd, viewX, viewY, shape,
                List.copyOf(constraints), List.copyOf(optimal), List.copyOf(flat), x1, x2, objectiveValue,
                List.copyOf(steps), message);
    }

    private static boolean spansArea(List<Point> points) {
        if (points.size() < 3) return false;
        Point origin = points.get(0);
        for (int i = 1; i < points.size() - 1; i++) {
            double cross = (points.get(i).x() - origin.x()) * (points.get(i + 1).y() - origin.y())
                    - (points.get(i).y() - origin.y()) * (points.get(i + 1).x() - origin.x());
            if (Math.abs(cross) > 1e-6)
                return true;
        }
        return false;
    }

    private static List<Point> vertices(List<Line> lines, LinearProgram program) {
        List<Point> found = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            for (int j = i + 1; j < lines.size(); j++) {
                Point point = intersect(lines.get(i), lines.get(j));
                if (point == null || point.x() < -1e-7 || point.y() < -1e-7)
                    continue;
                point = new Point(snap(Math.max(0, point.x())), snap(Math.max(0, point.y())));
                if (!feasible(point, program) || contains(found, point))
                    continue;
                found.add(point);
            }
        }
        return found;
    }

    private static boolean feasible(Point point, LinearProgram program) {
        if (point.x() < -1e-7 || point.y() < -1e-7) return false;
        for (int i = 0; i < program.constraints(); i++) {
            double value = program.coefficient(i, 0) * point.x() + program.coefficient(i, 1) * point.y();
            double rhs = program.rhs(i);
            double tolerance = 1e-7 * (1 + Math.abs(rhs));
            boolean holds = switch (program.relation(i)) {
                case LE -> value <= rhs + tolerance;
                case GE -> value >= rhs - tolerance;
                case EQ -> Math.abs(value - rhs) <= tolerance;
            };
            if (!holds) return false;
        }
        return true;
    }

    private static boolean zeroRowHolds(Relation relation, double rhs) {
        return switch (relation) {
            case LE -> rhs >= -EPS;
            case GE -> rhs <= EPS;
            case EQ -> Math.abs(rhs) <= EPS;
        };
    }

    private static List<Point> recessionDirections(List<Half> halves) {
        List<Point> rays = new ArrayList<>();
        for (Half half : halves) {
            if (Math.hypot(half.a, half.b) < EPS) continue;
            considerRay(rays, halves, -half.b, half.a);
            considerRay(rays, halves, half.b, -half.a);
        }
        return rays;
    }

    private static void considerRay(List<Point> rays, List<Half> halves, double dx, double dy) {
        double norm = Math.hypot(dx, dy);
        if (norm < EPS) return;
        dx /= norm;
        dy /= norm;
        for (Half half : halves) {
            if (half.a * dx + half.b * dy > 1e-7) return;
        }
        Point ray = new Point(snap(dx), snap(dy));
        if (!contains(rays, ray)) rays.add(ray);
    }

    private static double[] window(LinearProgram program, List<Point> vertices) {
        double maxX = 0;
        double maxY = 0;
        for (Point vertex : vertices) {
            maxX = Math.max(maxX, vertex.x());
            maxY = Math.max(maxY, vertex.y());
        }
        for (int i = 0; i < program.constraints(); i++) {
            double a1 = program.coefficient(i, 0);
            double a2 = program.coefficient(i, 1);
            double rhs = program.rhs(i);
            if (Math.abs(a1) > EPS) {
                double intercept = rhs / a1;
                if (intercept > 0)
                    maxX = Math.max(maxX, intercept);
            }
            if (Math.abs(a2) > EPS) {
                double intercept = rhs / a2;
                if (intercept > 0)
                    maxY = Math.max(maxY, intercept);
            }
        }
        if (maxX < 1)
            maxX = 5;
        if (maxY < 1)
            maxY = 5;
        return new double[]{maxX * 1.2, maxY * 1.2};
    }

    private static List<Point> rectangle(double viewX, double viewY) {
        return List.of(new Point(0, 0), new Point(viewX, 0), new Point(viewX, viewY), new Point(0, viewY));
    }

    private static List<Point> clip(List<Point> polygon, double a, double b, double rhs) {
        if (polygon.isEmpty()) return List.of();
        if (polygon.size() == 1) return inside(polygon.get(0), a, b, rhs) ? polygon : List.of();
        List<Point> output = new ArrayList<>();
        Point previous = polygon.get(polygon.size() - 1);
        boolean previousInside = inside(previous, a, b, rhs);
        for (Point current : polygon) {
            boolean currentInside = inside(current, a, b, rhs);
            if (currentInside) {
                if (!previousInside) output.add(edgeCross(previous, current, a, b, rhs));
                output.add(current);
            } else if (previousInside) {
                output.add(edgeCross(previous, current, a, b, rhs));
            }
            previous = current;
            previousInside = currentInside;
        }
        return clean(output);
    }

    private static boolean inside(Point point, double a, double b, double rhs) {
        return a * point.x() + b * point.y() <= rhs + 1e-7 * (1 + Math.abs(rhs));
    }

    private static Point edgeCross(Point from, Point to, double a, double b, double rhs) {
        double dx = to.x() - from.x();
        double dy = to.y() - from.y();
        double denom = a * dx + b * dy;
        if (Math.abs(denom) < 1e-12) return from;
        double t = (rhs - (a * from.x() + b * from.y())) / denom;
        t = Math.min(1, Math.max(0, t));
        return new Point(snap(from.x() + t * dx), snap(from.y() + t * dy));
    }

    private static List<Point> cutByLine(List<Point> polygon, double a, double b, double rhs) {
        if (polygon.size() <= 1) {
            return polygon.size() == 1 && onLine(polygon.get(0), a, b, rhs) ? polygon : List.of();
        }
        List<Point> hits = new ArrayList<>();
        int count = polygon.size();
        for (int i = 0; i < count; i++) {
            Point from = polygon.get(i);
            Point to = polygon.get((i + 1) % count);
            double fromValue = a * from.x() + b * from.y() - rhs;
            double toValue = a * to.x() + b * to.y() - rhs;
            double scale = 1e-7 * (1 + Math.abs(rhs));
            if (Math.abs(fromValue) <= scale)
                add(hits, from);
            if (fromValue * toValue < 0) {
                double t = fromValue / (fromValue - toValue);
                add(hits, new Point(snap(from.x() + t * (to.x() - from.x())), snap(from.y() + t * (to.y() - from.y()))));
            }
        }
        return endpoints(hits);
    }

    private static boolean onLine(Point point, double a, double b, double rhs) {
        return Math.abs(a * point.x() + b * point.y() - rhs) <= 1e-7 * (1 + Math.abs(rhs));
    }

    private static Point[] segmentInBox(double a, double b, double c, double maxX, double maxY) {
        if (Math.hypot(a, b) < EPS)
            return null;
        List<Point> hits = new ArrayList<>();
        addEdgeHit(hits, a, b, c, 0, 0, maxX, 0);
        addEdgeHit(hits, a, b, c, 0, maxY, maxX, maxY);
        addEdgeHit(hits, a, b, c, 0, 0, 0, maxY);
        addEdgeHit(hits, a, b, c, maxX, 0, maxX, maxY);
        List<Point> ends = endpoints(hits);
        if (ends.size() < 2)
            return null;
        return new Point[]{ends.get(0), ends.get(1)};
    }

    private static void addEdgeHit(List<Point> hits, double a, double b, double c, double x1, double y1, double x2, double y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double denom = a * dx + b * dy;
        double value = a * x1 + b * y1 - c;
        if (Math.abs(denom) < 1e-12) {
            if (Math.abs(value) <= 1e-7 * (1 + Math.abs(c))) {
                add(hits, new Point(x1, y1));
                add(hits, new Point(x2, y2));
            }
            return;
        }
        double t = -value / denom;
        if (t >= -1e-8 && t <= 1 + 1e-8) {
            t = Math.min(1, Math.max(0, t));
            add(hits, new Point(snap(x1 + t * dx), snap(y1 + t * dy)));
        }
    }

    private static Point intersect(Line first, Line second) {
        double det = first.a * second.b - second.a * first.b;
        if (Math.abs(det) < 1e-12)
            return null;
        return new Point((first.c * second.b - second.c * first.b) / det, (first.a * second.c - second.a * first.c) / det);
    }

    private static double boxExtreme(double c1, double c2, double viewX, double viewY, boolean maximum) {
        double[] values = {0, c1 * viewX, c2 * viewY, c1 * viewX + c2 * viewY};
        double extreme = values[0];
        for (double value : values)
            extreme = maximum ? Math.max(extreme, value) : Math.min(extreme, value);
        return extreme;
    }

    private static List<Point> endpoints(List<Point> points) {
        if (points.size() <= 2)
            return clean(points);
        Point first = points.get(0);
        Point second = points.get(0);
        double best = -1;
        for (Point left : points) {
            for (Point right : points) {
                double distance = Math.hypot(left.x() - right.x(), left.y() - right.y());
                if (distance > best) {
                    best = distance;
                    first = left;
                    second = right;
                }
            }
        }
        if (best < 1e-7)
            return List.of(first);
        return clean(List.of(first, second));
    }

    private static List<Point> orderAroundCentroid(List<Point> points) {
        double cx = 0;
        double cy = 0;
        for (Point point : points) {
            cx += point.x();
            cy += point.y();
        }
        cx /= points.size();
        cy /= points.size();
        double centerX = cx;
        double centerY = cy;
        List<Point> ordered = new ArrayList<>(points);
        ordered.sort(Comparator.comparingDouble(point -> Math.atan2(point.y() - centerY, point.x() - centerX)));
        return ordered;
    }

    private static List<Point> clean(List<Point> polygon) {
        List<Point> result = new ArrayList<>();
        for (Point point : polygon) {
            Point snapped = new Point(snap(point.x()), snap(point.y()));
            if (result.isEmpty() || !close(result.get(result.size() - 1), snapped))
                result.add(snapped);
        }
        if (result.size() > 1 && close(result.get(0), result.get(result.size() - 1)))
            result.remove(result.size() - 1);
        return result;
    }

    private static void add(List<Point> points, Point point) {
        Point snapped = new Point(snap(point.x()), snap(point.y()));
        if (!contains(points, snapped)) points.add(snapped);
    }

    private static boolean contains(List<Point> points, Point point) {
        for (Point existing : points)
            if (close(existing, point))
                return true;
        return false;
    }

    private static boolean close(Point left, Point right) {
        return Math.hypot(left.x() - right.x(), left.y() - right.y()) <= 1e-5;
    }

    private static double snap(double value) {
        double rounded = Math.rint(value * 1e9) / 1e9;
        return Math.abs(rounded) < 1e-9 ? 0 : rounded;
    }

    private static Step step(int index, String kind, int constraintIndex, String title, List<Point> polygon, Point from, Point to, boolean feasible) {
        return new Step(index, kind, constraintIndex, title, List.copyOf(clean(polygon)), from, to, feasible);
    }

    private static String constraintLabel(int index, double a1, double a2, Relation relation, double rhs) {
        String symbol = switch (relation) {
            case LE -> "≤";
            case GE -> "≥";
            case EQ -> "=";
        };
        return "(" + (index + 1) + ")  " + expression(a1, a2) + " " + symbol + " " + number(rhs);
    }

    private static String expression(double a1, double a2) {
        StringBuilder text = new StringBuilder();
        appendTerm(text, a1, "x1");
        appendTerm(text, a2, "x2");
        return text.isEmpty() ? "0" : text.toString();
    }

    private static void appendTerm(StringBuilder text, double coefficient, String name) {
        if (Math.abs(coefficient) < 1e-9)
            return;
        String magnitude = number(Math.abs(coefficient));
        String body = magnitude.equals("1") ? name : magnitude + name;
        if (text.isEmpty())
            text.append(coefficient < 0 ? "-" : "").append(body);
        else
            text.append(coefficient < 0 ? " - " : " + ").append(body);
    }

    private static String number(double value) {
        double snapped = snap(value);
        if (Math.abs(snapped - Math.rint(snapped)) < 1e-8)
            return Long.toString(Math.round(snapped));
        String text = String.format(Locale.US, "%.4f", snapped);
        return text.replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private record Line(double a, double b, double c) {}

    private record Half(double a, double b, double rhs) {}
}
