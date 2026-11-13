package dev.autotests.runtoggles;

import java.util.function.ToIntFunction;

/** Text cut to a width with «…» at the end. */
final class TextFit {

    private static final String ELLIPSIS = "…";

    private TextFit() {
    }

    /** The text if it fits maxWidth, else its longest beginning that fits together with «…»; an empty string if not even
     * one character does. width measures a string (pixels of the font in use). */
    static String fit(String text, int maxWidth, ToIntFunction<String> width) {
        if (width.applyAsInt(text) <= maxWidth) {
            return text;
        }
        int room = maxWidth - width.applyAsInt(ELLIPSIS);
        int low = 0;
        int high = text.length();
        while (low < high) {
            int middle = (low + high + 1) / 2;
            if (width.applyAsInt(text.substring(0, middle)) <= room) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        String head = text.substring(0, low).stripTrailing();
        return head.isEmpty() ? "" : head + ELLIPSIS;
    }
}
