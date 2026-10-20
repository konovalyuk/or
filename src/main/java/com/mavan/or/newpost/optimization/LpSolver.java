package com.mavan.or.newpost.optimization;

import java.util.Arrays;

/**
 * Two-phase simplex for
 *
 *   maximize c^T x
 *   subject to A x <= b,
 *              x >= 0.
 *
 * This is an in-project implementation based on the standard Stanford/KACTL
 * tableau formulation. The New Post MILP layer uses it only for LP relaxations.
 */
final class LpSolver {
    private static final double EPS = 1e-8;
    private static final double INF = Double.POSITIVE_INFINITY;
    private static final double NEG_INF = Double.NEGATIVE_INFINITY;

    private final int m;
    private final int n;
    private final int[] N;
    private final int[] B;
    private final double[][] D;

    LpSolver(double[][] A, double[] b, double[] c) {
        this.m = b.length;
        this.n = c.length;
        this.N = new int[n + 1];
        this.B = new int[m];
        this.D = new double[m + 2][n + 2];

        for (int i = 0; i < m; i++) {
            System.arraycopy(A[i], 0, D[i], 0, n);
        }
        for (int i = 0; i < m; i++) {
            B[i] = n + i;
            D[i][n] = -1.0;
            D[i][n + 1] = b[i];
        }
        for (int j = 0; j < n; j++) {
            N[j] = j;
            D[m][j] = -c[j];
        }
        N[n] = -1;
        D[m + 1][n] = 1.0;
    }

    private void pivot(int r, int s) {
        double[] row = D[r];
        double inv = 1.0 / row[s];

        for (int i = 0; i < m + 2; i++) {
            if (i == r || Math.abs(D[i][s]) <= EPS) continue;
            double[] target = D[i];
            double factor = target[s] * inv;
            for (int j = 0; j < n + 2; j++) {
                target[j] -= row[j] * factor;
            }
            target[s] = row[s] * factor;
        }

        for (int j = 0; j < n + 2; j++) {
            if (j != s) row[j] *= inv;
        }
        for (int i = 0; i < m + 2; i++) {
            if (i != r) D[i][s] *= -inv;
        }
        row[s] = inv;

        int tmp = B[r];
        B[r] = N[s];
        N[s] = tmp;
    }

    private boolean simplex(int phase) {
        int objectiveRow = m + phase - 1;

        while (true) {
            int s = -1;
            for (int j = 0; j <= n; j++) {
                if (N[j] == -phase) continue;
                if (s == -1
                        || D[objectiveRow][j] < D[objectiveRow][s] - EPS
                        || (Math.abs(D[objectiveRow][j] - D[objectiveRow][s]) <= EPS
                        && N[j] < N[s])) {
                    s = j;
                }
            }

            if (s == -1 || D[objectiveRow][s] >= -EPS) return true;

            int r = -1;
            for (int i = 0; i < m; i++) {
                if (D[i][s] <= EPS) continue;
                if (r == -1
                        || D[i][n + 1] / D[i][s] < D[r][n + 1] / D[r][s] - EPS
                        || (Math.abs(D[i][n + 1] / D[i][s] - D[r][n + 1] / D[r][s]) <= EPS
                        && B[i] < B[r])) {
                    r = i;
                }
            }

            if (r == -1) return false;
            pivot(r, s);
        }
    }

    double solve(double[] x) {
        int r = 0;
        for (int i = 1; i < m; i++) {
            if (D[i][n + 1] < D[r][n + 1]) r = i;
        }

        if (m > 0 && D[r][n + 1] < -EPS) {
            pivot(r, n);

            // Phase 1: find a feasible basis.
            if (!simplex(2) || D[m + 1][n + 1] < -EPS) {
                Arrays.fill(x, 0.0);
                return NEG_INF;
            }
            if (Math.abs(D[m + 1][n + 1]) > EPS) {
                Arrays.fill(x, 0.0);
                return NEG_INF;
            }

            for (int i = 0; i < m; i++) {
                if (B[i] != -1) continue;

                int s = -1;
                for (int j = 0; j <= n; j++) {
                    if (s == -1
                            || D[i][j] < D[i][s] - EPS
                            || (Math.abs(D[i][j] - D[i][s]) <= EPS
                            && N[j] < N[s])) {
                        s = j;
                    }
                }
                if (s >= 0) pivot(i, s);
            }
        }

        // Phase 2: optimize original objective.
        boolean bounded = simplex(1);
        Arrays.fill(x, 0.0);
        for (int i = 0; i < m; i++) {
            if (B[i] >= 0 && B[i] < n) {
                x[B[i]] = Math.max(0.0, D[i][n + 1]);
            }
        }

        return bounded ? D[m][n + 1] : INF;
    }
}
