package com.mavan.or.algorithms.transportation;

import com.mavan.or.algorithms.transportation.datamodels.TransTable;
import com.mavan.or.algorithms.transportation.datamodels.TransTableCell;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class TransClosedLoop {

    /**
     * Find the unique transportation closed loop associated with
     * the entering cell.
     * <p>
     * The returned list looks like:
     * <p>
     * entering
     * basic
     * basic
     * basic
     * ...
     * <p>
     * Consecutive cells alternate:
     * <p>
     * same row
     * same column
     * same row
     * same column
     * <p>
     * The cycle is closed by connecting the final cell back to
     * the entering cell.
     */
    public List<TransTableCell> findClosedLoop(TransTableCell entering, TransTable table) {
        List<TransTableCell> path = new ArrayList<>();
        path.add(entering);

        boolean found = depthFirstSearchCycle(entering, entering, path, table);

        return found ? new ArrayList<>(path) : null;
    }

    private boolean depthFirstSearchCycle(TransTableCell current, TransTableCell entering, List<TransTableCell> path, TransTable table) {
        /*
         * path size:
         *
         * 1 => entering
         * 2 => first horizontal/vertical basic cell
         * 3 => next cell
         * ...
         *
         * From the entering cell the first move is horizontal.
         */
        boolean needHorizontal = path.size() % 2 == 1;

        if (needHorizontal) {
            int row = current.row();

            for (int column = 0; column < table.getColumns(); column++) {
                if (column == current.column()) {
                    continue;
                }

                /*
                 * Can close the cycle?
                 */
                if (row == entering.row() && column == entering.column()) {
                    if (path.size() >= 4 && path.size() % 2 == 0) {
                        return true;
                    }
                    continue;
                }

                /*
                 * Horizontal move can go only through a basic cell.
                 */
                if (!table.basic()[row][column]) {
                    continue;
                }

                var next = new TransTableCell(row, column);
                if (containsCell(path, next)) {
                    continue;
                }
                path.add(next);
                if (depthFirstSearchCycle(next, entering, path, table)) {
                    return true;
                }
                path.remove(path.size() - 1);
            }
        } else {
            int column = current.column();

            for (int row = 0; row < table.getRows(); row++) {
                if (row == current.row()) {
                    continue;
                }

                /*
                 * Can close the cycle?
                 */
                if (row == entering.row() && column == entering.column()) {
                    if (path.size() >= 4 && path.size() % 2 == 0) {
                        return true;
                    }
                    continue;
                }

                /*
                 * Vertical move can go only through a basic cell.
                 */
                if (!table.basic()[row][column]) {
                    continue;
                }

                var next = new TransTableCell(row, column);
                if (containsCell(path, next)) {
                    continue;
                }
                path.add(next);
                if (depthFirstSearchCycle(next, entering, path, table)) {
                    return true;
                }
                path.remove(path.size() - 1);
            }
        }
        return false;
    }


    private boolean containsCell(List<TransTableCell> path, TransTableCell target) {
        for (TransTableCell cell : path) {
            if (cell.equals(target)) {
                return true;
            }
        }
        return false;
    }
}
