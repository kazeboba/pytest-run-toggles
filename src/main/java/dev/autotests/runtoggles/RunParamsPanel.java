package dev.autotests.runtoggles;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.InplaceButton;
import com.intellij.ui.JBColor;
import com.intellij.ui.JBIntSpinner;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;
import javax.swing.JList;
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

/** Options editor: one column, «Standard» (pytest options) and then «Custom», one row per option; the «+» at the right
 * of the «Params» title opens the window that adds an option. */
final class RunParamsPanel extends JPanel implements Disposable {

    private static final int MAX_INT = 1_000_000;
    private static final String INT = "-?\\d+";
    private static final JBColor DROP_COLOR = JBColor.namedColor("DragAndDrop.borderColor", new JBColor(0x2675BF, 0x2F65CA));
    private static final Border ROW_BORDER = JBUI.Borders.empty(1, 0);
    /** the line above a row: the dragged row goes before it (the same height as ROW_BORDER, so the rows do not jump) */
    private static final Border DROP_ABOVE = JBUI.Borders.customLine(DROP_COLOR, 2, 0, 0, 0);
    /** the line below the last row: the dragged row goes to the end of the block */
    private static final Border DROP_BELOW = JBUI.Borders.customLine(DROP_COLOR, 0, 0, 2, 0);

    private final Project project;
    private final RunParamsState state;
    private Map<String, String> intOptions = Map.of();
    /** the «Params» title with the «+» button: it stays on top while the rows are scrolled */
    private final JPanel header;
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
        JPanel titleRow = SectionHeader.of("Params", addButton());
        this.header = new JPanel(new BorderLayout());
        header.add(titleRow, BorderLayout.CENTER);
        header.setBorder(JBUI.Borders.empty(0, 6, 4, 6));
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
        // one column: Standard, then Custom; no gaps between the rows, the panel is kept low
        FormBuilder form = FormBuilder.createFormBuilder().setVerticalGap(0).addComponent(header("Standard"));
        state.params().stream().filter(param -> param.standard).forEach(param -> form.addComponent(row(param)));
        form.addComponent(header("Custom"));
        state.params().stream().filter(param -> !param.standard).forEach(param -> form.addComponent(row(param)));
        content = form.addComponentFillVertically(new JPanel(), 0).getPanel();
        content.setBorder(JBUI.Borders.empty(2, 6));
        content.setFocusable(true);
        removeAll();
        add(header, BorderLayout.NORTH);
        add(SectionHeader.framed(new JBScrollPane(content)), BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    /** Block header. */
    private static JComponent header(String title) {
        TitledSeparator separator = new TitledSeparator(title);
        separator.setBorder(JBUI.Borders.empty(0, 0, 2, 0));
        return separator;
    }

    /** «+»: opens the window that adds a standard or a custom option. */
    private JComponent addButton() {
        return new InplaceButton("Add a standard pytest option, a custom one or an option with a dropdown of values",
                AllIcons.General.Add, event -> {
            AddParamDialog dialog = new AddParamDialog(project, intOptions);
            if (dialog.showAndGet()) {
                state.add(dialog.result());
                changed();
                rebuild();
            }
        });
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
        JBCheckBox box = new JBCheckBox(param.hasTitle() ? param.title : param.name, param.on());
        box.addActionListener(event -> {
            param.setOn(box.isSelected());
            changed();
        });
        JPanel left = new JPanel(new BorderLayout(JBUI.scale(4), 0));
        left.add(handle, BorderLayout.WEST);
        left.add(box, BorderLayout.CENTER);
        row.add(left, BorderLayout.WEST);
        rows.add(new RowRef(param, row));
        if (param.hasChoices()) {
            row.add(choicesBox(param), BorderLayout.CENTER);
        } else if (param.binary) {
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

    /** The dropdown of an option with choices ('Samsung: R58M123456A'): the selected one is what the option passes. */
    private JComponent choicesBox(RunParamsState.Param param) {
        ComboBox<RunParamsState.Choice> combo = new ComboBox<>(param.choices.toArray(new RunParamsState.Choice[0]));
        combo.setRenderer(new SimpleListCellRenderer<RunParamsState.Choice>() {
            // a subclass, not SimpleListCellRenderer.create(...): both overloads of create are on the way out
            @Override
            public void customize(@NotNull JList<? extends RunParamsState.Choice> list, RunParamsState.Choice choice, int index,
                                  boolean selected, boolean hasFocus) {
                setText(choice == null ? "" : choice.display());
            }
        });
        param.select(Math.max(0, Math.min(param.selected, param.choices.size() - 1)));
        combo.setSelectedIndex(param.selected);
        combo.addActionListener(event -> {
            param.select(combo.getSelectedIndex());
            changed();
        });
        return combo;
    }

    private void changed() {
        RunParamsSync.apply(project, this);
    }

    /** Drag a row by its handle within its block (its column); a line shows the drop position. */
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
            List<RowRef> block = rows.stream().filter(ref -> ref.param().standard == param.standard).toList();
            List<Rectangle> cells = block.stream()
                    .map(ref -> SwingUtilities.convertRectangle(ref.row().getParent(), ref.row().getBounds(), content)).toList();
            block.forEach(ref -> ref.row().setBorder(ROW_BORDER));
            int index = RowDrop.insertBefore(cells, y);
            if (index < block.size()) {
                before = block.get(index).param();
                block.get(index).row().setBorder(DROP_ABOVE);
            } else {
                before = null;
                if (!block.isEmpty()) {
                    block.get(block.size() - 1).row().setBorder(DROP_BELOW);
                }
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
