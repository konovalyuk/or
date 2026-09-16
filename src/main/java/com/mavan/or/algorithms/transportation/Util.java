package com.mavan.or.algorithms.transportation;

import com.mavan.or.algorithms.transportation.datamodels.TransTable;
import com.mavan.or.algorithms.transportation.datamodels.TransTableCell;

public class Util {

    public static double sum(double[] values) {
        double result = 0.0;
        for (double value : values) {
            result += value;
        }
        return result;
    }

    public static double[][] deepCopy(double[][] source) {
        if (source == null) {
            return null;
        }
        double[][] result = new double[source.length][];
        for (int i = 0; i < source.length; i++) {
            result[i] = source[i].clone();
        }
        return result;
    }

    public static boolean[][] deepCopy(boolean[][] source) {
        if (source == null) {
            return null;
        }
        boolean[][] result = new boolean[source.length][];
        for (int i = 0; i < source.length; i++) {
            result[i] = source[i].clone();
        }
        return result;
    }

    public static int compareCells(TransTableCell first, TransTableCell second) {
        if (first.row() != second.row()) {
            return Integer.compare(first.row(), second.row());
        }
        return Integer.compare(first.column(), second.column());
    }

    public static int countBasicVariables(TransTable table) {
        int count = 0;
        for (int i = 0; i < table.getRows(); i++) {
            for (int j = 0; j < table.getColumns(); j++) {
                if (table.basic()[i][j]) {
                    count++;
                }
            }
        }
        return count;
    }

}
