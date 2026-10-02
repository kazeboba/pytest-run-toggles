package dev.autotests.runtoggles;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;

/** Opens the Pytest Params panel (values, order, adding options). */
final class OpenParamsPanelAction extends AnAction implements DumbAware {

    static final String TOOL_WINDOW_ID = "Pytest Params";

    OpenParamsPanelAction(String text, Icon icon) {
        super(text, "Open the Pytest Params panel", icon);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        ToolWindow window = project == null ? null : ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID);
        if (window != null) {
            window.activate(null);
        }
    }
}
