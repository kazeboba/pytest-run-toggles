package dev.autotests.runtoggles;

import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;

/** Marks of the module list: green ✔ passed, red ✖ failed, grey ○ not run. */
final class StatusIcon implements Icon {

    private static final JBColor GREEN = new JBColor(0x59A869, 0x5FB865);
    private static final JBColor RED = new JBColor(0xDB5860, 0xF75464);
    private static final JBColor GREY = new JBColor(0x818594, 0x868A91);

    private static final StatusIcon PASSED = new StatusIcon(TestStatus.PASSED);
    private static final StatusIcon FAILED = new StatusIcon(TestStatus.FAILED);
    private static final StatusIcon NOT_RUN = new StatusIcon(TestStatus.NOT_RUN);

    private final TestStatus status;

    private StatusIcon(TestStatus status) {
        this.status = status;
    }

    static StatusIcon of(TestStatus status) {
        return switch (status) {
            case PASSED -> PASSED;
            case FAILED -> FAILED;
            case NOT_RUN -> NOT_RUN;
        };
    }

    @Override
    public void paintIcon(Component component, Graphics graphics, int x, int y) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.translate(x, y);
            g.scale(JBUI.scale(1f), JBUI.scale(1f));
            g.setStroke(new BasicStroke(status == TestStatus.NOT_RUN ? 1.5f : 2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            switch (status) {
                case PASSED -> {
                    Path2D check = new Path2D.Float();
                    check.moveTo(3.2, 8.4);
                    check.lineTo(6.6, 11.8);
                    check.lineTo(12.8, 4.4);
                    g.setColor(GREEN);
                    g.draw(check);
                }
                case FAILED -> {
                    g.setColor(RED);
                    g.draw(new Line2D.Float(4, 4, 12, 12));
                    g.draw(new Line2D.Float(12, 4, 4, 12));
                }
                case NOT_RUN -> {
                    g.setColor(GREY);
                    g.draw(new Ellipse2D.Float(3.5f, 3.5f, 9, 9));
                }
            }
        } finally {
            g.dispose();
        }
    }

    @Override
    public int getIconWidth() {
        return JBUI.scale(16);
    }

    @Override
    public int getIconHeight() {
        return JBUI.scale(16);
    }
}
