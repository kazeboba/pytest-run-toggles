package dev.autotests.runtoggles;

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.actionSystem.ex.CheckboxAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.jetbrains.python.testing.PyAbstractTestConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Run tab toolbar of pytest runs: one checkbox per option (single click) and a gear that opens the panel. */
public final class RunParamsToolbarGroup extends ActionGroup implements DumbAware {

    /** The key of LangDataKeys.EXECUTION_ENVIRONMENT (deprecated; its replacement ExecutionDataKeys is missing before 2024.3):
     * data keys are matched by name. */
    private static final DataKey<ExecutionEnvironment> EXECUTION_ENVIRONMENT = DataKey.create("executionEnvironment");

    private static final AnAction OPEN_PANEL = new OpenParamsPanelAction("Pytest Params", AllIcons.General.Settings);

    /** the same instances across toolbar updates — otherwise the checkboxes are recreated and flicker */
    private final Map<RunParamsState.Param, ParamCheckbox> checkboxes = new WeakHashMap<>();

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }

    /** Only for pytest (and Autodetect) runs; shown when the tab's run profile is unknown. */
    @Override
    public void update(@NotNull AnActionEvent event) {
        ExecutionEnvironment environment = event.getData(EXECUTION_ENVIRONMENT);
        event.getPresentation().setEnabledAndVisible(event.getProject() != null
                && (environment == null || RunParamsSync.pytestLike(environment.getRunProfile()) != null));
    }

    @Override
    public @NotNull AnAction[] getChildren(@Nullable AnActionEvent event) {
        Project project = event == null ? null : event.getProject();
        if (project == null) {
            return EMPTY_ARRAY;
        }
        List<AnAction> actions = new ArrayList<>();
        actions.add(Separator.getInstance());
        RunParamsState state = RunParamsState.getInstance(project);
        if (state.getState().toolbarOptions) {
            for (boolean standard : new boolean[]{true, false}) {
                state.params().stream().filter(param -> param.standard == standard)
                        .forEach(param -> actions.add(checkboxes.computeIfAbsent(param, ParamCheckbox::new)));
            }
        }
        if (state.getState().toolbarProgress) {
            actions.add(Separator.getInstance());
            actions.add(ModuleCleanupAction.INSTANCE);
            actions.add(ModuleStatsAction.INSTANCE);
        }
        actions.add(OPEN_PANEL);
        return actions.toArray(AnAction.EMPTY_ARRAY);
    }

    /** A run of a saved configuration: the panel doesn't reach it, so its checkboxes edit its own arguments. */
    static @Nullable RunnerAndConfigurationSettings savedRun(@NotNull AnActionEvent event) {
        ExecutionEnvironment environment = event.getData(EXECUTION_ENVIRONMENT);
        RunnerAndConfigurationSettings settings = environment == null ? null : environment.getRunnerAndConfigurationSettings();
        return settings != null && !settings.isTemporary() && RunParamsSync.pytestLike(settings.getConfiguration()) != null
                ? settings : null;
    }

    /** '--maxfail=1' → 'maxfail=1', a 0/1 option → 'debugging'; short options keep the dash ('-x', '-k smoke'). */
    static String label(RunParamsState.Param param) {
        String text = param.label();
        return text.startsWith("--") ? text.substring(2) : text;
    }

    private static final class ParamCheckbox extends CheckboxAction implements DumbAware {

        private final RunParamsState.Param param;

        ParamCheckbox(RunParamsState.Param param) {
            this.param = param;
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.EDT;
        }

        @Override
        public void update(@NotNull AnActionEvent event) {
            super.update(event);
            RunnerAndConfigurationSettings saved = savedRun(event);
            String own = saved == null ? null : ConfigArgs.label(arguments(saved), param);
            event.getPresentation().setText(own != null ? own : label(param), false);
            event.getPresentation().setDescription(saved == null ? null : "Option of the saved configuration '"
                    + saved.getName() + "': changes its own arguments, Rerun applies them");
        }

        @Override
        public boolean isSelected(@NotNull AnActionEvent event) {
            RunnerAndConfigurationSettings saved = savedRun(event);
            return saved == null ? param.on() : ConfigArgs.isOn(arguments(saved), param);
        }

        @Override
        public void setSelected(@NotNull AnActionEvent event, boolean state) {
            RunnerAndConfigurationSettings saved = savedRun(event);
            if (saved != null) {
                RunParamsSync.set(saved.getConfiguration(), ConfigArgs.toggle(arguments(saved), param, state));
                return;
            }
            param.setOn(state);
            if (event.getProject() != null) {
                RunParamsSync.apply(event.getProject(), this);
            }
        }

        private static String arguments(RunnerAndConfigurationSettings saved) {
            PyAbstractTestConfiguration test = RunParamsSync.pytestLike(saved.getConfiguration());
            return test == null ? "" : test.getAdditionalArguments();
        }
    }
}
