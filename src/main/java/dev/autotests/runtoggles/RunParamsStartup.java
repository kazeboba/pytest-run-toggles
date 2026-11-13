package dev.autotests.runtoggles;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

/** On project open, brings the pytest template and temporary configurations in line with the panel and makes the
 * PytestParams tab in the Run panel. */
public final class RunParamsStartup implements StartupActivity.DumbAware {

    @Override
    public void runActivity(@NotNull Project project) {
        ApplicationManager.getApplication().invokeLater(() -> {
            RunParamsSync.apply(project);
            // the PytestParams tab is in the Run panel from the start, so the panel can be opened before any run
            try {
                PytestParamsTab.ensure(project);
            } catch (RuntimeException | LinkageError failure) {
                Logger.getInstance(RunParamsStartup.class).warn("PytestParams: the tab could not be made", failure);
            }
        }, project.getDisposed());
    }
}
