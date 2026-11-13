package dev.autotests.runtoggles;

import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RowDropTest {

    /** three rows of 20 px one under another */
    private static final List<Rectangle> COLUMN = List.of(
            new Rectangle(0, 0, 300, 20), new Rectangle(0, 20, 300, 20), new Rectangle(0, 40, 300, 20));

    @Test
    void upperHalfOfARowGoesBeforeItLowerHalfAfterIt() {
        assertEquals(1, RowDrop.insertBefore(COLUMN, 22));
        assertEquals(2, RowDrop.insertBefore(COLUMN, 35));
    }

    @Test
    void aboveTheFirstRowGoesToTheStart() {
        assertEquals(0, RowDrop.insertBefore(COLUMN, -5));
        assertEquals(0, RowDrop.insertBefore(COLUMN, 3));
    }

    @Test
    void belowTheLastRowMeansTheEndOfTheColumn() {
        assertEquals(3, RowDrop.insertBefore(COLUMN, 55));
        assertEquals(3, RowDrop.insertBefore(COLUMN, 400));
    }

    @Test
    void emptyColumnDropsAtZero() {
        assertEquals(0, RowDrop.insertBefore(List.of(), 5));
    }
}
