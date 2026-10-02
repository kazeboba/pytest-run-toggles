package dev.autotests.runtoggles;

import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.actionSystem.ex.CheckboxAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.jetbrains.python.testing.PyTestConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Run tab toolbar of pytest runs: one checkbox per option (single click) and a gear that opens the panel. */
public final class RunParamsToolbarGroup extends ActionGroup implements DumbAware {

    private static final AnAction OPEN_PANEL = new OpenParamsPanelAction("Pytest Params", AllIcons.General.Settings);

    /** the same instances across toolbar updates — otherwise the checkboxes are recreated and flicker */
    private final Map<RunParamsState.Param, ParamCheckbox> checkboxes = new WeakHashMap<>();

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }

    /** Only for pytest runs; shown when the tab's run profile is unknown. */
    @Override
    public void update(@NotNull AnActionEvent event) {
        ExecutionEnvironment environment = event.getData(LangDataKeys.EXECUTION_ENVIRONMENT);
        event.getPresentation().setEnabledAndVisible(event.getProject() != null
                && (environment == null || environment.getRunProfile() instanceof PyTestConfiguration));
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
            actions.add(ModuleStatsAction.INSTANCE);
        }
        actions.add(OPEN_PANEL);
        return actions.toArray(AnAction.EMPTY_ARRAY);
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
            event.getPresentation().setText(label(param), false);
        }

        @Override
        public boolean isSelected(@NotNull AnActionEvent event) {
            return param.on();
        }

        @Override
        public void setSelected(@NotNull AnActionEvent event, boolean state) {
            param.setOn(state);
            if (event.getProject() != null) {
                RunParamsSync.apply(event.getProject(), this);
            }
        }
    }
}
