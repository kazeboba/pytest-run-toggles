package dev.autotests.runtoggles;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.project.Project;
import com.intellij.util.execution.ParametersListUtil;
import com.jetbrains.python.testing.PyAbstractTestConfiguration;
import com.jetbrains.python.testing.PyTestConfiguration;
import com.jetbrains.python.testing.PythonTestConfigurationType;
import com.jetbrains.python.testing.autoDetectTests.PyAutoDetectTestConfiguration;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Additional Arguments of the pytest template and temporary (▶) configurations = enabled panel rows. */
final class RunParamsSync {

    private RunParamsSync() {
    }

    static void apply(Project project) {
        apply(project, null);
    }

    /** source — who changed the options: a RunParamsTopic subscriber may skip its own changes. */
    static void apply(Project project, Object source) {
        seedOnce(project);
        RunParamsState state = RunParamsState.getInstance(project);
        String args = state.arguments();
        List<String> deselect = ModuleProgress.deselectArgs(project, state.getState());
        RunManager runManager = RunManager.getInstance(project);
        if (state.getState().manageTemplate) {
            set(template(runManager), args);
            set(runManager.getConfigurationTemplate(PythonTestConfigurationType.getInstance().getAutoDetectFactory())
                    .getConfiguration(), args);
        }
        for (RunnerAndConfigurationSettings settings : runManager.getAllSettings()) {
            if (settings.isTemporary() && state.getState().manageTemporary) {
                set(settings.getConfiguration(), argsFor(project, settings.getConfiguration(), args, deselect));
            }
        }
        project.getMessageBus().syncPublisher(RunParamsTopic.TOPIC).changed(source);
    }

    /** First run: the panel is filled from the current template arguments, so ▶ behaves as before.
     * Every time: marks 0/1 options from conftest. */
    static void seedOnce(Project project) {
        RunParamsState state = RunParamsState.getInstance(project);
        if (state.getState().seeded) {
            markBinary(project);
            return;
        }
        if (template(RunManager.getInstance(project)) instanceof PyTestConfiguration pytest) {
            for (RunParamsState.Param param : RunParamsState.parse(pytest.getAdditionalArguments(), false)) {
                param.standard = StandardOption.isStandard(param.name);
                param.numeric = param.value != null && param.value.trim().matches("-?\\d+");
                state.add(List.of(param));
            }
        }
        state.getState().seeded = true;
        markBinary(project);
    }

    /** conftest int options with default 0/1 become value checkboxes. A row that was disabled
     * (argument not passed) gets the default, so pytest sees the same as before. */
    static void markBinary(Project project) {
        markBinary(RunParamsState.getInstance(project).params(), ConftestOptions.intDefaults(project));
    }

    static void markBinary(List<RunParamsState.Param> params, Map<String, String> conftestInts) {
        for (RunParamsState.Param param : params) {
            String fallback = conftestInts.get(param.name);
            boolean zeroOne = "0".equals(fallback) || "1".equals(fallback);
            if (zeroOne) {
                param.conftestDefault = fallback;
            }
            if (param.binary || !zeroOne || (param.value != null && !param.value.matches("[01]"))) {
                continue;
            }
            if (!param.enabled || param.value == null) {
                param.value = fallback;
            }
            param.enabled = true;
            param.binary = true;
        }
    }

    /** Clean up: --deselect goes only to the run of the counted module or folder itself, runs of other targets
     * (and of a module inside the counted folder) stay complete. */
    static String argsFor(Project project, RunConfiguration configuration, String args, List<String> deselect) {
        String scope = ModuleProgress.scopeOf(project, configuration);
        if (deselect.isEmpty() || scope == null
                || !scope.equals(RunParamsState.getInstance(project).getState().progressModule)) {
            return args;
        }
        List<String> tokens = new ArrayList<>(ParametersListUtil.parse(args));
        tokens.addAll(deselect);
        return ParametersListUtil.join(tokens);
    }

    static void set(RunConfiguration configuration, String args) {
        PyAbstractTestConfiguration test = pytestLike(configuration);
        if (test != null) {
            test.setAdditionalArguments(args);
        }
    }

    /** pytest configurations and Autodetect ones (they run pytest when it is installed) take the options. */
    static @Nullable PyAbstractTestConfiguration pytestLike(@Nullable Object configuration) {
        return configuration instanceof PyTestConfiguration || configuration instanceof PyAutoDetectTestConfiguration
                ? (PyAbstractTestConfiguration) configuration : null;
    }

    private static RunConfiguration template(RunManager runManager) {
        return runManager.getConfigurationTemplate(PythonTestConfigurationType.getInstance().getPyTestFactory())
                .getConfiguration();
    }
}
