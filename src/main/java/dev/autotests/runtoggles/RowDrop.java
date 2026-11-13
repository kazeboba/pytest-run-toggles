package dev.autotests.runtoggles;

import java.awt.Rectangle;
import java.util.List;

/** Dropping a dragged row into a column of rows. */
final class RowDrop {

    private RowDrop() {
    }

    /** The index of the row the dragged one goes before: the first row whose centre is below the point
     * (cells.size() — to the end of the column). */
    static int insertBefore(List<Rectangle> cells, int y) {
        for (int i = 0; i < cells.size(); i++) {
            if (y < cells.get(i).getCenterY()) {
                return i;
            }
        }
        return cells.size();
    }
}
