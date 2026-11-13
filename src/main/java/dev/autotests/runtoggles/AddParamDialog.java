package dev.autotests.runtoggles;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JList;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** The window of the «+» button: one standard pytest option from the list, or a custom one typed in
 * ({@code --param=value}, {@code Title: --param=value}, {@code [Title:] --serial={Samsung: ID1, Pixel: ID2}}). */
final class AddParamDialog extends DialogWrapper {

    private static final String INT = "-?\\d+";

    private final Map<String, String> intOptions;
    private final ComboBox<Object> standard;
    private final JBTextField custom = new JBTextField();
    private List<RunParamsState.Param> result = List.of();

    AddParamDialog(Project project, Map<String, String> intOptions) {
        super(project, true);
        this.intOptions = intOptions;
        List<Object> items = new ArrayList<>();
        items.add("none");
        items.addAll(List.of(StandardOption.values()));
        standard = new ComboBox<>(items.toArray());
        standard.setRenderer(new SimpleListCellRenderer<Object>() {
            // a subclass, not SimpleListCellRenderer.create(...): both overloads of create are on the way out
            @Override
            public void customize(@NotNull JList<? extends Object> list, Object value, int index, boolean selected,
                                  boolean hasFocus) {
                setText(value instanceof StandardOption option ? option.display() : "— choose a standard option —");
            }
        });
        setTitle("Add Parameter");
        init();
    }

    /** The rows to add (standard or custom), after OK. */
    List<RunParamsState.Param> result() {
        return result;
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        custom.getEmptyText().setText("--param=value");
        JBLabel hint = new JBLabel("<html>Custom formats:<br>"
                + "&nbsp;&nbsp;--param=value<br>"
                + "&nbsp;&nbsp;Title: --param=value — the checkbox is named Title<br>"
                + "&nbsp;&nbsp;[Title:] --serial={Samsung: R58M123456A, Pixel: 1A2B3C4D5E6F} — one option, a dropdown of values"
                + "</html>");
        hint.setForeground(JBColor.GRAY);
        return FormBuilder.createFormBuilder()
                .addLabeledComponent("Standard option:", standard)
                .addLabeledComponent("Custom:", custom)
                .addComponent(hint)
                .getPanel();
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
        return standard;
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        return build(new ArrayList<>());
    }

    @Override
    protected void doOKAction() {
        List<RunParamsState.Param> built = new ArrayList<>();
        if (build(built) == null) {
            result = built;
            super.doOKAction();
        }
    }

    /** Fills the rows from the dialog; null — fine, else what is wrong. */
    private ValidationInfo build(List<RunParamsState.Param> out) {
        Object picked = standard.getSelectedItem();
        String text = custom.getText().trim();
        boolean hasStandard = picked instanceof StandardOption;
        if (hasStandard && !text.isEmpty()) {
            return new ValidationInfo("Fill only one: a standard option or a custom one", custom);
        }
        if (hasStandard) {
            out.add(((StandardOption) picked).toParam());
            return null;
        }
        if (text.isEmpty()) {
            return new ValidationInfo("Choose a standard option or type a custom one", standard);
        }
        List<RunParamsState.Param> parsed = RunParamsState.parseInput(text, false);
        if (parsed.isEmpty()) {
            return new ValidationInfo("Nothing to add: type --param=value", custom);
        }
        if (RunParamsState.splitTitle(text) != null && parsed.size() != 1) {
            return new ValidationInfo("A title goes with exactly one option", custom);
        }
        for (RunParamsState.Param param : parsed) {
            if (param.hasChoices()) {
                continue;
            }
            boolean knownInt = StandardOption.isInt(param.name) || intOptions.containsKey(param.name);
            if (param.value == null && knownInt) {
                param.value = intOptions.getOrDefault(param.name, "0");
            }
            boolean integer = param.value != null && param.value.trim().matches(INT);
            if (knownInt && !integer) {
                return new ValidationInfo(param.name + " expects an integer", custom);
            }
            param.numeric = integer;
        }
        out.addAll(parsed);
        return null;
    }
}
