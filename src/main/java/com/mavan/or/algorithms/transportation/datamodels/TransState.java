package com.mavan.or.algorithms.transportation.datamodels;

import com.mavan.or.algorithms.transportation.Util;

import java.util.List;

/**
 * Complete state of one transportation-simplex stage.
 * <p>
 * It is intentionally exposed as data so the browser version can
 * serialize this object and render every iteration as a separate table.
 *
 * @param evaluation Evaluation:
 *                   <p>
 *                   R_ij = u_i + v_j - c_ij
 *                   <p>
 *                   Positive R_ij means that the nonbasic variable can enter for a minimization problem.
 */
public record TransState(int iteration, double[][] allocationBefore, boolean[][] basicBefore,
                         double[] u, double[] v, double[][] evaluation,
                         TransTableCell entering, TransTableCell leaving,
                         List<TransTableCell> cycle, double theta,
                         double[][] allocationAfter, boolean[][] basicAfter,
                         double totalCost, boolean optimal) {

    public TransState {
        allocationBefore = Util.deepCopy(allocationBefore);
        basicBefore = Util.deepCopy(basicBefore);
        u = u.clone();
        v = v.clone();
        evaluation = Util.deepCopy(evaluation);
        cycle = cycle == null ? List.of() : List.copyOf(cycle);
        allocationAfter = Util.deepCopy(allocationAfter);
        basicAfter = Util.deepCopy(basicAfter);
    }

    @Override
    public double[][] allocationBefore() {
        return Util.deepCopy(allocationBefore);
    }

    @Override
    public boolean[][] basicBefore() {
        return Util.deepCopy(basicBefore);
    }

    @Override
    public double[] u() {
        return u.clone();
    }

    @Override
    public double[] v() {
        return v.clone();
    }

    @Override
    public double[][] evaluation() {
        return Util.deepCopy(evaluation);
    }

    @Override
    public List<TransTableCell> cycle() {
        return cycle;
    }

    @Override
    public double[][] allocationAfter() {
        return Util.deepCopy(allocationAfter);
    }

    @Override
    public boolean[][] basicAfter() {
        return Util.deepCopy(basicAfter);
    }

}
