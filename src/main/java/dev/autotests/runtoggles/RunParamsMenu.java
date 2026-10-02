package dev.autotests.runtoggles;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** Status bar menu: option checkboxes by block and «Edit…». */
final class RunParamsMenu {

    private RunParamsMenu() {
    }

    static AnAction[] items(Project project) {
        List<AnAction> actions = new ArrayList<>();
        RunParamsState state = RunParamsState.getInstance(project);
        for (boolean standard : new boolean[]{true, false}) {
            actions.add(new Separator(standard ? "Standard" : "Custom"));
            state.params().stream().filter(param -> param.standard == standard)
                    .forEach(param -> actions.add(new ParamToggle(param)));
        }
        actions.add(Separator.getInstance());
        actions.add(new OpenParamsPanelAction("Edit…", null));
        return actions.toArray(AnAction.EMPTY_ARRAY);
    }

    /** Checkbox of one option; text as in the arguments ('--maxfail=0', '-k smoke'). */
    private static final class ParamToggle extends ToggleAction implements DumbAware {

        private final RunParamsState.Param param;

        ParamToggle(RunParamsState.Param param) {
            this.param = param;
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.EDT;
        }

        @Override
        public void update(@NotNull AnActionEvent event) {
            super.update(event);
            event.getPresentation().setText(param.label(), false);
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
