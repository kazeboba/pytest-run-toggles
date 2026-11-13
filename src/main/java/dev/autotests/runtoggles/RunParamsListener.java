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

    /** RunManager itself fires this while loading configurations, so it must not be requested here (cyclic
     * service initialization): before the first seeding the startup activity seeds and syncs everything. */
    @Override
    public void runConfigurationAdded(@NotNull RunnerAndConfigurationSettings settings) {
        RunParamsState state = RunParamsState.getInstance(project);
        if (settings.isTemporary() && state.getState().manageTemporary && state.getState().seeded
                && !RunParamsSync.isCustomTarget(settings.getConfiguration())) {
            RunParamsSync.markBinary(project);
            RunParamsSync.set(settings.getConfiguration(), RunParamsSync.argsFor(project, settings.getConfiguration(),
                    state.arguments(), ModuleProgress.deselectArgs(project, state.getState())));
        }
    }
}
