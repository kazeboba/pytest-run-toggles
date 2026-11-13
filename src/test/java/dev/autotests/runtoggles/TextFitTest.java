package dev.autotests.runtoggles;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextFitTest {

    /** one pixel per character */
    private static int width(String text) {
        return text.length();
    }

    @Test
    void textThatFitsStaysAsItIs() {
        assertEquals("Registration", TextFit.fit("Registration", 12, TextFitTest::width));
        assertEquals("Registration", TextFit.fit("Registration", 40, TextFitTest::width));
    }

    @Test
    void longTextIsCutWithEllipsisWithinTheWidth() {
        String fitted = TextFit.fit("Registration, remind me later", 10, TextFitTest::width);
        assertEquals("Registrat…", fitted);
        assertEquals(10, fitted.length());
    }

    @Test
    void spaceBeforeTheCutIsNotKept() {
        assertEquals("Remind…", TextFit.fit("Remind me later", 8, TextFitTest::width));
    }

    @Test
    void nothingFitsGivesEmptyText() {
        assertEquals("", TextFit.fit("Registration", 1, TextFitTest::width));
        assertEquals("", TextFit.fit("Registration", 0, TextFitTest::width));
    }
}
