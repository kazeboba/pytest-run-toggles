package dev.autotests.runtoggles;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

/** On project open, brings the pytest template and temporary configurations in line with the panel. */
public final class RunParamsStartup implements StartupActivity.DumbAware {

    @Override
    public void runActivity(@NotNull Project project) {
        ApplicationManager.getApplication().invokeLater(() -> RunParamsSync.apply(project), project.getDisposed());
    }
}
