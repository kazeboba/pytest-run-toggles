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

/** The window of the «+» button: one standard pytest option from the list, or a custom one in separate fields: the
 * title of the checkbox (optional), the parameter ({@code --param}, {@code --param=value}) and the options of a
 * dropdown (optional, {@code Samsung: ID1, Pixel: ID2}). The earlier single-line formats
 * ({@code Title: --param=value}, {@code [Title:] --serial={Samsung: ID1}}) still work in the parameter field. */
final class AddParamDialog extends DialogWrapper {

    private static final String INT = "-?\\d+";
    private static final String OPTION_NAME = "-{1,2}[A-Za-z][\\w-]*";

    private final Map<String, String> intOptions;
    private final ComboBox<Object> standard;
    private final JBTextField title = new JBTextField();
    private final JBTextField parameter = new JBTextField();
    private final JBTextField options = new JBTextField();
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
        title.getEmptyText().setText("optional: the name of the checkbox, e.g. Device");
        parameter.getEmptyText().setText("--param  or  --param=value  (several: --retest=1 --shard)");
        options.getEmptyText().setText("optional: Samsung: R58M123456A, Pixel: 1A2B3C4D5E6F");
        JBLabel hint = new JBLabel("<html>With options the parameter is only a name (--serial) and the option gets a dropdown "
                + "of values: the checkbox passes the chosen one.<br>"
                + "Options are written as <i>Label: value</i>, separated by commas.</html>");
        hint.setForeground(JBColor.GRAY);
        return FormBuilder.createFormBuilder()
                .addLabeledComponent("Standard option:", standard)
                .addSeparator(8)
                .addLabeledComponent("Title:", title)
                .addLabeledComponent("Parameter:", parameter)
                .addLabeledComponent("Options:", options)
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
        String titleText = title.getText().trim();
        String parameterText = parameter.getText().trim();
        String optionsText = options.getText().trim();
        boolean hasStandard = picked instanceof StandardOption;
        boolean hasCustom = !titleText.isEmpty() || !parameterText.isEmpty() || !optionsText.isEmpty();
        if (hasStandard && hasCustom) {
            return new ValidationInfo("Fill only one: a standard option or a custom one", parameter);
        }
        if (hasStandard) {
            out.add(((StandardOption) picked).toParam());
            return null;
        }
        if (!hasCustom) {
            return new ValidationInfo("Choose a standard option or fill in the parameter", standard);
        }
        if (parameterText.isEmpty()) {
            return new ValidationInfo("Type the parameter, e.g. --serial", parameter);
        }
        if (!optionsText.isEmpty()) {
            return dropdown(parameterText, titleText, optionsText, out);
        }
        List<RunParamsState.Param> parsed = RunParamsState.parseInput(parameterText, false);
        if (parsed.isEmpty()) {
            return new ValidationInfo("Nothing to add: type --param=value", parameter);
        }
        if (!titleText.isEmpty()) {
            if (parsed.size() != 1) {
                return new ValidationInfo("A title goes with exactly one parameter", title);
            }
            parsed.get(0).title = titleText;
        } else if (RunParamsState.splitTitle(parameterText) != null && parsed.size() != 1) {
            return new ValidationInfo("A title goes with exactly one parameter", parameter);
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
                return new ValidationInfo(param.name + " expects an integer", parameter);
            }
            param.numeric = integer;
        }
        out.addAll(parsed);
        return null;
    }

    /** One parameter with a dropdown: the name from the parameter field, the values from the options field. */
    private ValidationInfo dropdown(String name, String titleText, String optionsText, List<RunParamsState.Param> out) {
        if (!name.matches(OPTION_NAME)) {
            return new ValidationInfo("With options the parameter is only a name, e.g. --serial", parameter);
        }
        List<RunParamsState.Choice> values = RunParamsState.parseChoices(optionsText);
        if (values.isEmpty()) {
            return new ValidationInfo("Options: Samsung: R58M123456A, Pixel: 1A2B3C4D5E6F", options);
        }
        RunParamsState.Param param = new RunParamsState.Param(name, null, false);
        param.choices = values;
        param.select(0);
        param.title = titleText.isEmpty() ? null : titleText;
        out.add(param);
        return null;
    }
}
