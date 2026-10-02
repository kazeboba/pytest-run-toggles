package dev.autotests.runtoggles;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

public final class RunParamsToolWindowFactory implements ToolWindowFactory, DumbAware {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        RunParamsSync.seedOnce(project);
        RunParamsPanel panel = new RunParamsPanel(project);
        Disposer.register(toolWindow.getDisposable(), panel);
        toolWindow.getContentManager().addContent(ContentFactory.getInstance().createContent(panel, "", false));
    }
}
