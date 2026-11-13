package dev.autotests.runtoggles;

import com.intellij.ui.JBColor;
import com.intellij.ui.TitledSeparator;
import com.intellij.util.ui.JBUI;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;

/** The title row of a column of the PytestParams tab: a title with its line, and the buttons of the column at the right.
 * Both columns use it, so the titles («Standard» on the left, «Tests to run» on the right) stand on one line whatever
 * the buttons are; and the outline of the scrolled area under it. */
final class SectionHeader {

    /** (not called HEIGHT: inside the anonymous panel that name is ImageObserver.HEIGHT, which is 2) */
    private static final int ROW_HEIGHT = 30;

    private SectionHeader() {
    }

    static JPanel of(String title, JComponent... buttons) {
        JPanel row = new JPanel(new GridBagLayout()) {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(super.getPreferredSize().width, JBUI.scale(ROW_HEIGHT));
            }
        };
        // the cells are centred on the height of the row: the title and the buttons keep their own sizes
        GridBagConstraints at = new GridBagConstraints();
        at.weightx = 1;
        at.fill = GridBagConstraints.HORIZONTAL;
        at.gridx = 0;
        row.add(new TitledSeparator(title), at);
        // every button has a cell of its own (not a flowing panel: it would put the last button on a second line, out of
        // sight, when the room is a little short)
        at.weightx = 0;
        at.fill = GridBagConstraints.NONE;
        at.anchor = GridBagConstraints.EAST;
        at.gridx = 0;
        for (JComponent button : buttons) {
            at.gridx++;
            at.insets = JBUI.insetsLeft(4);
            row.add(button, at);
        }
        return row;
    }

    /** The scrolled area of a column in a thin outline, with the same margins on both sides of the tab: the outline of
     * the left column is the same as the one of the right column. */
    static JPanel framed(JScrollPane scroll) {
        scroll.setBorder(JBUI.Borders.customLine(JBColor.border(), 1));
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(JBUI.Borders.empty(0, 6, 6, 6));
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }
}
