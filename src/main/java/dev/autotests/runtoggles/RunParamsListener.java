package dev.autotests.runtoggles;

import com.intellij.execution.RunManagerListener;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/** A new temporary pytest configuration (▶) gets the panel arguments right away. */
public final class RunParamsListener implements RunManagerListener {

    private final Project project;

    public RunParamsListener(Project project) {
        this.project = project;
    }

    @Override
    public void runConfigurationAdded(@NotNull RunnerAndConfigurationSettings settings) {
        if (settings.isTemporary()) {
            RunParamsSync.seedOnce(project);
            RunParamsState state = RunParamsState.getInstance(project);
            RunParamsSync.set(settings.getConfiguration(), RunParamsSync.argsFor(project, settings.getConfiguration(),
                    state.arguments(), ModuleProgress.deselectArgs(project, state.getState())));
        }
    }
}
