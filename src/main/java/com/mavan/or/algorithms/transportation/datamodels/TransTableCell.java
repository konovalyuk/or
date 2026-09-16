package com.mavan.or.algorithms.transportation.datamodels;

/**
 * Cell coordinate.
 * Internally indexes are zero-based.
 * Printed indexes are one-based.
 */
public record TransTableCell(int row, int column) {

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TransTableCell(int row1, int column1))) return false;
        return row == row1 && column == column1;
    }

    @Override
    public String toString() {
        return "x" + (row + 1) + (column + 1);
    }
}
