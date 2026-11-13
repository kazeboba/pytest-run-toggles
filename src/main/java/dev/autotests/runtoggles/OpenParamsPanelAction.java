package dev.autotests.runtoggles;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;

/** Opens the Run panel on the PytestParams tab (the options, the choice of what to run, the progress buttons). */
final class OpenParamsPanelAction extends AnAction implements DumbAware {

    OpenParamsPanelAction(String text, Icon icon) {
        super(text, "Open the PytestParams tab", icon);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project != null) {
            PytestParamsTab.show(project);
        }
    }
}
