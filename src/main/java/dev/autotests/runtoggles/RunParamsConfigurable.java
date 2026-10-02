package dev.autotests.runtoggles;

import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;
import javax.swing.JPanel;

/** Settings → Tools → Pytest Run Params: where the options are written and what the Run tab toolbar shows. */
public final class RunParamsConfigurable implements SearchableConfigurable {

    private final Project project;
    private final JBCheckBox template = new JBCheckBox("pytest run configuration template");
    private final JBCheckBox temporary = new JBCheckBox("Temporary configurations (▶ gutter and Rerun)");
    private final JBCheckBox options = new JBCheckBox("Option checkboxes");
    private final JBCheckBox progress = new JBCheckBox("Module progress counters (✔ ✖ ○) and Clean up module");

    public RunParamsConfigurable(Project project) {
        this.project = project;
    }

    @Override
    public @NotNull String getId() {
        return "pytest.run.params";
    }

    @Override
    public String getDisplayName() {
        return "Pytest Run Params";
    }

    @Override
    public JComponent createComponent() {
        JBLabel replaceNote = new JBLabel("Enabled options replace the Additional Arguments of these configurations. "
                + "Saved configurations are never changed.", UIUtil.ComponentStyle.SMALL, UIUtil.FontColor.BRIGHTER);
        JBLabel statusBarNote = new JBLabel("The status bar widget is shown or hidden from the status bar context menu.",
                UIUtil.ComponentStyle.SMALL, UIUtil.FontColor.BRIGHTER);
        return FormBuilder.createFormBuilder()
                .addComponent(new TitledSeparator("Write options to"))
                .addComponent(template)
                .addComponent(temporary)
                .addComponent(replaceNote)
                .addComponent(new TitledSeparator("Run tab toolbar of pytest runs"))
                .addComponent(options)
                .addComponent(progress)
                .addComponent(statusBarNote)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
    }

    private RunParamsState.Data data() {
        return RunParamsState.getInstance(project).getState();
    }

    @Override
    public boolean isModified() {
        RunParamsState.Data data = data();
        return template.isSelected() != data.manageTemplate || temporary.isSelected() != data.manageTemporary
                || options.isSelected() != data.toolbarOptions || progress.isSelected() != data.toolbarProgress;
    }

    @Override
    public void apply() {
        RunParamsState.Data data = data();
        data.manageTemplate = template.isSelected();
        data.manageTemporary = temporary.isSelected();
        data.toolbarOptions = options.isSelected();
        data.toolbarProgress = progress.isSelected();
        RunParamsSync.apply(project);
    }

    @Override
    public void reset() {
        RunParamsState.Data data = data();
        template.setSelected(data.manageTemplate);
        temporary.setSelected(data.manageTemporary);
        options.setSelected(data.toolbarOptions);
        progress.setSelected(data.toolbarProgress);
    }
}
