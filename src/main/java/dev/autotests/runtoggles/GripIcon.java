package dev.autotests.runtoggles;

import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;

import javax.swing.Icon;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** Row drag handle: 2×3 dots (AllIcons 2023.1 has no Drag icon). */
final class GripIcon implements Icon {

    private static int dot() {
        return JBUI.scale(3);
    }

    private static int gap() {
        return JBUI.scale(2);
    }

    @Override
    public void paintIcon(Component component, Graphics graphics, int x, int y) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(JBColor.GRAY);
            for (int row = 0; row < 3; row++) {
                for (int column = 0; column < 2; column++) {
                    g.fillOval(x + column * (dot() + gap()), y + row * (dot() + gap()), dot(), dot());
                }
            }
        } finally {
            g.dispose();
        }
    }

    @Override
    public int getIconWidth() {
        return dot() * 2 + gap();
    }

    @Override
    public int getIconHeight() {
        return dot() * 3 + gap() * 2;
    }
}
