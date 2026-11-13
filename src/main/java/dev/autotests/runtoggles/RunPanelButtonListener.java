package dev.autotests.runtoggles;

import com.intellij.execution.Executor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** A click on the plugin's button on the sidebar (the empty tool window of {@link RunPanelButtonFactory}) hides it
 * again and opens the Run panel on the PytestParams tab. While the project is still starting (a layout saved with the
 * window open) it is only hidden. The IDE's own Run button opens the standard Run panel: if the PytestParams tab is the
 * selected one, the latest real run is brought to the front instead. */
public final class RunPanelButtonListener implements ToolWindowManagerListener {

    private final Project project;

    public RunPanelButtonListener(Project project) {
        this.project = project;
    }

    @Override
    public void toolWindowShown(@NotNull ToolWindow toolWindow) {
        if (DefaultRunExecutor.getRunExecutorInstance().getToolWindowId().equals(toolWindow.getId())) {
            showStandardRun();
            return;
        }
        if (!RunPanelButtonFactory.ID.equals(toolWindow.getId())) {
            return;
        }
        boolean started = StartupManager.getInstance(project).postStartupActivityPassed();
        ApplicationManager.getApplication().invokeLater(() -> {
            toolWindow.hide();
            if (started) {
                PytestParamsTab.show(project);
            }
        }, project.getDisposed());
    }

    /** The Run window was opened by the IDE's own Run button (not by us) with our tab selected: the standard panel is
     * the latest real run, so its tab is brought to the front. */
    private void showStandardRun() {
        if (PytestParamsTab.justShown()) {
            return;
        }
        ApplicationManager.getApplication().invokeLater(() -> {
            Executor executor = DefaultRunExecutor.getRunExecutorInstance();
            RunContentManager contents = RunContentManager.getInstance(project);
            RunContentDescriptor selected = contents.getSelectedContent();
            if (selected == null || !PytestParamsTab.isTab(selected)) {
                return;
            }
            List<RunContentDescriptor> descriptors = contents.getAllDescriptors();
            int real = PytestParamsTab.lastRealIndex(descriptors.stream().map(PytestParamsTab::isTab).toList());
            if (real >= 0) {
                contents.toFrontRunContent(executor, descriptors.get(real));
            }
        }, project.getDisposed());
    }
}
