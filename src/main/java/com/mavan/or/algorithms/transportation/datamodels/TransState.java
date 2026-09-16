package com.mavan.or.algorithms.transportation.datamodels;

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
public record TransState(int iteration, double[][] allocation, boolean[][] basic, double[] u, double[] v,
                         double[][] evaluation, TransTableCell entering, TransTableCell leaving,
                         List<TransTableCell> cycle, double theta, double totalCost, boolean optimal) {

}
