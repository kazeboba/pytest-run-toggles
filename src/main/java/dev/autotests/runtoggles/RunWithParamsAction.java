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
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.testing.ConfigurationTarget;
import com.jetbrains.python.testing.PyAbstractTestConfiguration;
import com.jetbrains.python.testing.PyTestsConfigurationProducer;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** Project view: the selected folder or test file runs as a temporary pytest configuration with the panel options.
 * The standard Run reuses a saved configuration with the same target (e.g. a folder preset) and keeps its arguments. */
public final class RunWithParamsAction extends AnAction implements DumbAware {

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        VirtualFile[] files = event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        event.getPresentation().setEnabledAndVisible(event.getProject() != null && files != null && files.length == 1
                && isTestTarget(files[0].getName(), files[0].isDirectory()));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project != null) {
            run(project, ConfigurationContext.getFromContext(event.getDataContext(), event.getPlace()), false);
        }
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
