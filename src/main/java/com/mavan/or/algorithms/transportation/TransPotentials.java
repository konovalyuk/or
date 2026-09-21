package com.mavan.or.algorithms.transportation;

import com.mavan.or.algorithms.transportation.datamodels.TransTable;
import com.mavan.or.algorithms.transportation.datamodels.TransTableCell;
import org.springframework.stereotype.Service;

import java.util.Arrays;

@Service
public class TransPotentials {

    /**
     * Calculate the transportation potentials:
     * <p>
     * u_i + v_j = c_ij
     * <p>
     * for every basic cell.
     * <p>
     * One potential may be fixed arbitrarily; here:
     * <p>
     * u_1 = 0
     * <p>
     * is used.
     */
    public void calculatePotentials(double[] u, double[] v, TransTable table) {
        Arrays.fill(u, Double.NaN);
        Arrays.fill(v, Double.NaN);

        boolean[] uKnown = new boolean[table.getRows()];
        boolean[] vKnown = new boolean[table.getColumns()];

        u[0] = 0.0;
        uKnown[0] = true;

        boolean changed = true;

        while (changed) {
            changed = false;

            for (int i = 0; i < table.getRows(); i++) {
                for (int j = 0; j < table.getColumns(); j++) {
                    if (!table.basic()[i][j]) {
                        continue;
                    }

                    /*
                     * u_i + v_j = c_ij
                     */
                    if (uKnown[i] && !vKnown[j]) {
                        v[j] = table.costAt(i, j) - u[i];
                        vKnown[j] = true;
                        changed = true;
                    } else if (vKnown[j] && !uKnown[i]) {
                        u[i] = table.costAt(i, j) - v[j];
                        uKnown[i] = true;
                        changed = true;
                    }
                }
            }
        }

        /*
         * With a valid transportation basis containing rows+columns-1
         * independent basic variables, all potentials should be determined.
         *
         * The fallback below protects the code against unexpected malformed
         * input or externally modified state.
         */
        for (int i = 0; i < table.getRows(); i++) {
            if (!uKnown[i]) {
                u[i] = 0.0;
            }
        }

        for (int j = 0; j < table.getColumns(); j++) {
            if (!vKnown[j]) {
                v[j] = 0.0;
            }
        }
    }

    /**
     * Transportation-simplex optimality test.
     * <p>
     * Taha's evaluation is:
     * <p>
     * R_ij = u_i + v_j - c_ij
     * <p>
     * for nonbasic cells.
     * <p>
     * For minimization:
     * <p>
     * R_ij <= 0  for every nonbasic cell => optimal
     * <p>
     * If some:
     * <p>
     * R_ij > 0
     * <p>
     * that cell is a candidate to enter the basis.
     * <p>
     * The implementation selects the largest positive R_ij.
     */
    public TransTableCell findEnteringVariable(double[] u, double[] v, double[][] evaluation, TransTable table) {
        TransTableCell entering = null;
        double bestEvaluation = 0.0;

        for (int i = 0; i < table.getRows(); i++) {
            for (int j = 0; j < table.getColumns(); j++) {
                evaluation[i][j] = u[i] + v[j] - table.costAt(i, j);

                if (table.basic()[i][j]) {
                    continue;
                }

                if (evaluation[i][j] > bestEvaluation + TransTable.EPS) {
                    bestEvaluation = evaluation[i][j];
                    entering = new TransTableCell(i, j);
                } else if (entering != null && evaluation[i][j] > TransTable.EPS
                        && Math.abs(evaluation[i][j] - bestEvaluation) <= TransTable.EPS
                        && Util.compareCells(new TransTableCell(i, j), entering) < 0) {
                    entering = new TransTableCell(i, j);
                }
            }
        }

        return entering;
    }

}
