package dev.autotests.runtoggles;

import com.intellij.execution.ExecutionListener;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.execution.ParametersListUtil;
import com.jetbrains.python.run.targetBasedConfiguration.PyRunTargetVariant;
import com.jetbrains.python.testing.ConfigurationTarget;
import com.jetbrains.python.testing.PyAbstractTestConfiguration;
import com.jetbrains.python.testing.PyTestsConfigurationProducer;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** A folder or test file runs as a temporary pytest configuration with the panel options (the Run button of the
 * not started Run tab, Clean Up Module). The standard Run reuses a saved configuration with the same target
 * (e.g. a folder preset) and keeps its arguments, this one does not. */
final class RunWithParamsAction {

    private RunWithParamsAction() {
    }

    /** cleanup — a one-off clean-up run of the counted module: passed tests are skipped,
     * and the temporary configuration gets the panel arguments back once the run has started. */
    static void run(Project project, ConfigurationContext context, boolean cleanup) {
        // A new configuration from the context, without the lookup of existing ones that the standard Run does
        ConfigurationFromContext created = RunConfigurationProducer.getInstance(PyTestsConfigurationProducer.class)
                .createConfigurationFromContext(context);
        if (created == null) {
            return;
        }
        PyAbstractTestConfiguration pytest = RunParamsSync.pytestLike(created.getConfiguration());
        if (pytest == null) {
            Messages.showWarningDialog(project, "The default test runner of the project is neither pytest nor "
                    + "Autodetect (Settings → Tools → Python Integrated Tools).", "Run with Pytest Params");
            return;
        }
        RunManager runManager = RunManager.getInstance(project);
        RunnerAndConfigurationSettings settings = temporaryWithTarget(runManager, pytest.getTarget());
        if (settings == null) {
            settings = created.getConfigurationSettings();
            runManager.setUniqueNameIfNeeded(settings);
            runManager.setTemporaryConfiguration(settings);
        }
        // Explicitly: the options must reach this run even when temporary configurations are not managed
        RunParamsState state = RunParamsState.getInstance(project);
        RunParamsSync.set(settings.getConfiguration(), RunParamsSync.argsFor(project, settings.getConfiguration(),
                state.arguments(), ModuleProgress.deselectArgs(project, state.getState(), cleanup)));
        runManager.setSelectedConfiguration(settings);
        if (cleanup) {
            restoreArgumentsOnStart(project, settings);
        }
        ProgramRunnerUtil.executeConfiguration(settings, DefaultRunExecutor.getRunExecutorInstance());
    }

    /** Runs the ticked tests: a temporary configuration with the target 'custom' — nothing but the arguments: the options
     * of the panel and the paths and node ids of the selection. It is not counted by the progress counters (they
     * follow a module or a folder). */
    static void runSelection(Project project, ConfigurationContext context, List<String> ids) {
        ConfigurationFromContext created = RunConfigurationProducer.getInstance(PyTestsConfigurationProducer.class)
                .createConfigurationFromContext(context);
        PyAbstractTestConfiguration pytest = created == null ? null : RunParamsSync.pytestLike(created.getConfiguration());
        if (pytest == null) {
            Messages.showWarningDialog(project, "The default test runner of the project is neither pytest nor "
                    + "Autodetect (Settings → Tools → Python Integrated Tools).", "Run");
            return;
        }
        pytest.getTarget().setTargetType(PyRunTargetVariant.CUSTOM);
        pytest.getTarget().setTarget("");
        RunManager runManager = RunManager.getInstance(project);
        RunnerAndConfigurationSettings settings = temporaryWithTarget(runManager, pytest.getTarget());
        if (settings == null) {
            settings = created.getConfigurationSettings();
            settings.setName("pytest: selection");
            runManager.setUniqueNameIfNeeded(settings);
            runManager.setTemporaryConfiguration(settings);
        }
        List<String> tokens = new ArrayList<>(ParametersListUtil.parse(RunParamsState.getInstance(project).arguments()));
        tokens.addAll(ids);
        RunParamsSync.set(settings.getConfiguration(), ParametersListUtil.join(tokens));
        runManager.setSelectedConfiguration(settings);
        ProgramRunnerUtil.executeConfiguration(settings, DefaultRunExecutor.getRunExecutorInstance());
    }

    /** The one-off --deselect must not stay in the temporary configuration (Rerun and ▶ would repeat it): when the run
     * has started (the arguments are read) or has not started, the panel arguments are applied again. */
    private static void restoreArgumentsOnStart(Project project, RunnerAndConfigurationSettings settings) {
        Disposable once = Disposer.newDisposable("pytest-params-cleanup-run");
        Disposer.register(project, once);
        project.getMessageBus().connect(once).subscribe(ExecutionManager.EXECUTION_TOPIC, new ExecutionListener() {
            @Override
            public void processStarted(@NotNull String executorId, @NotNull ExecutionEnvironment environment,
                                       @NotNull ProcessHandler handler) {
                restore(environment);
            }

            @Override
            public void processNotStarted(@NotNull String executorId, @NotNull ExecutionEnvironment environment) {
                restore(environment);
            }

            private void restore(ExecutionEnvironment environment) {
                if (settings.equals(environment.getRunnerAndConfigurationSettings())) {
                    Disposer.dispose(once);
                    ApplicationManager.getApplication().invokeLater(() -> RunParamsSync.apply(project), project.getDisposed());
                }
            }
        });
    }

    /** A temporary run of the same target is reused, so repeated runs don't pile up 'pytest in tests (1)'. */
    private static RunnerAndConfigurationSettings temporaryWithTarget(RunManager runManager, ConfigurationTarget target) {
        for (RunnerAndConfigurationSettings settings : runManager.getAllSettings()) {
            PyAbstractTestConfiguration pytest = RunParamsSync.pytestLike(settings.getConfiguration());
            if (settings.isTemporary() && pytest != null
                    && pytest.getTarget().getTargetType() == target.getTargetType()
                    && Objects.equals(pytest.getTarget().getTarget(), target.getTarget())) {
                return settings;
            }
        }
        return null;
    }

    /** A folder or a pytest module by the default discovery patterns (test_*.py, *_test.py). */
    static boolean isTestTarget(String name, boolean directory) {
        return directory || name.endsWith(".py") && (name.startsWith("test_") || name.endsWith("_test.py"));
    }
}
