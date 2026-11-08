package dev.autotests.runtoggles;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.InplaceButton;
import com.intellij.ui.JBColor;
import com.intellij.ui.JBIntSpinner;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Pytest Params panel: «Standard» (pytest options, «+» button) and «Custom» (input field) blocks, one row per option. */
final class RunParamsPanel extends JPanel implements Disposable {

    private static final int MAX_INT = 1_000_000;
    private static final String INT = "-?\\d+";
    private static final JBColor DROP_COLOR = JBColor.namedColor("DragAndDrop.borderColor", new JBColor(0x2675BF, 0x2F65CA));
    private static final Border ROW_BORDER = JBUI.Borders.empty(2, 0);
    private static final Border DROP_ABOVE = BorderFactory.createCompoundBorder(
            JBUI.Borders.customLine(DROP_COLOR, 2, 0, 0, 0), JBUI.Borders.empty(0, 0, 2, 0));
    private static final Border DROP_BELOW = BorderFactory.createCompoundBorder(
            JBUI.Borders.empty(2, 0, 0, 0), JBUI.Borders.customLine(DROP_COLOR, 0, 0, 2, 0));

    private final Project project;
    private final RunParamsState state;
    private Map<String, String> intOptions = Map.of();
    /** panel content — receives focus on Enter in a number field */
    private JPanel content;
    /** rows of the current rendering — used to find the drop position while dragging */
    private final List<RowRef> rows = new ArrayList<>();

    private record RowRef(RunParamsState.Param param, JPanel row) {
    }

    RunParamsPanel(Project project) {
        super(new BorderLayout());
        this.project = project;
        this.state = RunParamsState.getInstance(project);
        // changes from the Run tab toolbar and the status bar; own changes are skipped so input focus is kept
        project.getMessageBus().connect(this).subscribe(RunParamsTopic.TOPIC, source -> {
            if (source != this) {
                rebuild();
            }
        });
        rebuild();
    }

    @Override
    public void dispose() {
    }

    private void rebuild() {
        intOptions = ConftestOptions.intDefaults(project);
        rows.clear();
        FormBuilder form = FormBuilder.createFormBuilder().addComponent(header("Standard", this::chooseStandard));
        state.params().stream().filter(param -> param.standard).forEach(param -> form.addComponent(row(param)));
        form.addComponent(header("Custom", null));
        state.params().stream().filter(param -> !param.standard).forEach(param -> form.addComponent(row(param)));
        form.addComponent(customInput());
        content = form.addComponentFillVertically(new JPanel(), 0).getPanel();
        content.setBorder(JBUI.Borders.empty(4, 8));
        content.setFocusable(true);
        removeAll();
        add(new JBScrollPane(content), BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    /** Block header; onAdd — «+» button, gets the header as the popup anchor
     * (the InplaceButton event source is a MouseEvent, not the button). */
    private static JComponent header(String title, Consumer<JComponent> onAdd) {
        JPanel header = new JPanel(new BorderLayout());
        header.add(new TitledSeparator(title), BorderLayout.CENTER);
        if (onAdd != null) {
            header.add(new InplaceButton("Add pytest option", AllIcons.General.Add,
                    event -> onAdd.accept(header)), BorderLayout.EAST);
        }
        return header;
    }

    private boolean isInt(RunParamsState.Param param) {
        return param.numeric || StandardOption.isInt(param.name) || intOptions.containsKey(param.name);
    }

    private JComponent row(RunParamsState.Param param) {
        JPanel row = new JPanel(new BorderLayout(JBUI.scale(6), 0));
        row.setBorder(ROW_BORDER);
        JBLabel handle = new JBLabel(new GripIcon());
        handle.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        RowDrag drag = new RowDrag(param);
        handle.addMouseListener(drag);
        handle.addMouseMotionListener(drag);
        JBCheckBox box = new JBCheckBox(param.name, param.on());
        box.addActionListener(event -> {
            param.setOn(box.isSelected());
            changed();
        });
        JPanel left = new JPanel(new BorderLayout(JBUI.scale(4), 0));
        left.add(handle, BorderLayout.WEST);
        left.add(box, BorderLayout.CENTER);
        row.add(left, BorderLayout.WEST);
        rows.add(new RowRef(param, row));
        if (param.binary) {
            // 0/1 option: the checkbox itself is the value
        } else if (isInt(param)) {
            row.add(numberField(param), BorderLayout.CENTER);
        } else if (param.value != null) {
            JBTextField value = new JBTextField(param.value);
            value.getDocument().addDocumentListener(new DocumentAdapter() {
                @Override
                protected void textChanged(@NotNull DocumentEvent event) {
                    param.value = value.getText();
                    changed();
                }
            });
            row.add(value, BorderLayout.CENTER);
        }
        row.add(new InplaceButton("Remove", AllIcons.Actions.Close, event -> {
            state.params().remove(param);
            changed();
            rebuild();
        }), BorderLayout.EAST);
        return row;
    }

    /** Number field: a non-integer value (old row, flag) is replaced with the conftest default or 0. */
    private JComponent numberField(RunParamsState.Param param) {
        String current = param.value != null && param.value.trim().matches(INT) ? param.value.trim()
                : intOptions.getOrDefault(param.name, "0");
        int number;
        try {
            number = (int) Math.max(0, Math.min(MAX_INT, Long.parseLong(current)));
        } catch (NumberFormatException tooLong) {
            number = MAX_INT;
        }
        if (!String.valueOf(number).equals(param.value)) {
            param.value = String.valueOf(number);
            changed();
        }
        JBIntSpinner spinner = new JBIntSpinner(number, 0, MAX_INT);
        spinner.addChangeListener(event -> {
            param.value = String.valueOf(spinner.getNumber());
            changed();
        });
        // Enter: the field has committed the value, move focus away
        if (spinner.getEditor() instanceof JSpinner.DefaultEditor editor) {
            editor.getTextField().addActionListener(event -> content.requestFocusInWindow());
        }
        return spinner;
    }

    private JComponent customInput() {
        JBTextField input = new JBTextField();
        input.getEmptyText().setText("--param=value");
        input.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent event) {
                input.putClientProperty("JComponent.outline", null);
            }
        });
        Runnable add = () -> {
            List<RunParamsState.Param> parsed = RunParamsState.parse(input.getText(), false);
            for (RunParamsState.Param param : parsed) {
                boolean knownInt = StandardOption.isInt(param.name) || intOptions.containsKey(param.name);
                if (param.value == null && knownInt) {
                    param.value = intOptions.getOrDefault(param.name, "0");
                }
                boolean integer = param.value != null && param.value.trim().matches(INT);
                if (knownInt && !integer) {
                    error(input, param.name + " expects an integer");
                    return;
                }
                param.numeric = integer;
            }
            if (!parsed.isEmpty()) {
                state.add(parsed);
                changed();
                rebuild();
            }
        };
        input.addActionListener(event -> add.run());
        JPanel row = new JPanel(new BorderLayout(JBUI.scale(6), 0));
        row.add(input, BorderLayout.CENTER);
        row.add(new InplaceButton("Add", AllIcons.General.Add, event -> add.run()), BorderLayout.EAST);
        return row;
    }

    private static void error(JBTextField input, String message) {
        input.putClientProperty("JComponent.outline", "error");
        input.repaint();
        JBPopupFactory.getInstance()
                .createHtmlTextBalloonBuilder(message, MessageType.ERROR, null)
                .setFadeoutTime(3000)
                .createBalloon()
                .show(RelativePoint.getSouthOf(input), Balloon.Position.below);
    }

    private void chooseStandard(JComponent anchor) {
        JBPopupFactory.getInstance()
                .createPopupChooserBuilder(List.of(StandardOption.values()))
                .setRenderer(new SimpleListCellRenderer<StandardOption>() {
                    // a subclass, not SimpleListCellRenderer.create(...): both overloads of create are on the way out
                    @Override
                    public void customize(@NotNull JList<? extends StandardOption> list, StandardOption option, int index,
                                          boolean selected, boolean hasFocus) {
                        setText(option == null ? "" : option.display());
                    }
                })
                .setItemChosenCallback(option -> {
                    state.add(List.of(option.toParam()));
                    changed();
                    rebuild();
                })
                .createPopup()
                .showUnderneathOf(anchor);
    }

    private void changed() {
        RunParamsSync.apply(project, this);
    }

    /** Drag a row by its handle within its block; a line shows the drop position. */
    private final class RowDrag extends MouseAdapter {

        private final RunParamsState.Param param;
        /** insert before this option; null — at the end of the block */
        private RunParamsState.Param before;
        private boolean dragging;

        RowDrag(RunParamsState.Param param) {
            this.param = param;
        }

        @Override
        public void mouseDragged(MouseEvent event) {
            dragging = true;
            int y = SwingUtilities.convertPoint(event.getComponent(), event.getPoint(), content).y;
            before = null;
            RowRef last = null;
            for (RowRef ref : rows) {
                if (ref.param().standard != param.standard) {
                    continue;
                }
                ref.row().setBorder(ROW_BORDER);
                last = ref;
                Rectangle bounds = SwingUtilities.convertRectangle(ref.row().getParent(), ref.row().getBounds(), content);
                if (before == null && y < bounds.getCenterY()) {
                    before = ref.param();
                    ref.row().setBorder(DROP_ABOVE);
                }
            }
            if (before == null && last != null) {
                last.row().setBorder(DROP_BELOW);
            }
        }

        @Override
        public void mouseReleased(MouseEvent event) {
            if (!dragging) {
                return;
            }
            dragging = false;
            if (before != param) {
                List<RunParamsState.Param> params = state.params();
                params.remove(param);
                params.add(before != null ? params.indexOf(before) : lastIndexOf(params, param.standard) + 1, param);
                changed();
            }
            rebuild();
        }
    }

    private static int lastIndexOf(List<RunParamsState.Param> params, boolean standard) {
        for (int i = params.size() - 1; i >= 0; i--) {
            if (params.get(i).standard == standard) {
                return i;
            }
        }
        return params.size() - 1;
    }
}
