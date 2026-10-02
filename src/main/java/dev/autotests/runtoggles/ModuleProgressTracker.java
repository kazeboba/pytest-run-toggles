package dev.autotests.runtoggles;

import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.sm.runner.SMTRunnerEventsAdapter;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Counts test results of a module: a module run of another file switches the module and starts from scratch;
 * folder runs and single tests of other modules are ignored. Skipped tests stay not run. */
public final class ModuleProgressTracker extends SMTRunnerEventsAdapter {

    private final Project project;
    /** run root → module it reports to */
    private final Map<SMTestProxy.SMRootTestProxy, String> runs = Collections.synchronizedMap(new WeakHashMap<>());

    public ModuleProgressTracker(Project project) {
        this.project = project;
    }

    @Override
    public void onTestingStarted(@NotNull SMTestProxy.SMRootTestProxy root) {
        TestConsoleProperties properties = root.getTestConsoleProperties();
        RunProfile profile = properties == null ? null : properties.getConfiguration();
        if (!(profile instanceof RunConfiguration configuration)) {
            return;
        }
        RunParamsState state = RunParamsState.getInstance(project);
        String module = ModuleProgress.moduleOf(project, configuration);
        if (module != null) {
            state.trackModule(module);
            runs.put(root, module);
        } else if (ModuleProgress.isInModule(configuration, state.getState().progressModule)) {
            runs.put(root, state.getState().progressModule);
        }
    }

    @Override
    public void onTestFinished(@NotNull SMTestProxy test) {
        String module = test.isLeaf() ? runs.get(rootOf(test)) : null;
        String key = module == null ? null : ModuleProgress.testKey(test.getLocationUrl(), module);
        if (key == null) {
            return;
        }
        if (test.isDefect()) {
            RunParamsState.getInstance(project).record(key, false);
        } else if (test.getMagnitudeInfo() == TestStateInfo.Magnitude.PASSED_INDEX) {
            RunParamsState.getInstance(project).record(key, true);
        }
    }

    @Override
    public void onTestingFinished(@NotNull SMTestProxy.SMRootTestProxy root) {
        // Clean up module: passed tests changed — refresh --deselect of the module run
        if (runs.remove(root) != null && RunParamsState.getInstance(project).getState().cleanup) {
            ApplicationManager.getApplication().invokeLater(() -> RunParamsSync.apply(project, this), project.getDisposed());
        }
    }

    private static SMTestProxy.SMRootTestProxy rootOf(SMTestProxy test) {
        AbstractTestProxy node = test;
        while (node != null && !(node instanceof SMTestProxy.SMRootTestProxy)) {
            node = node.getParent();
        }
        return (SMTestProxy.SMRootTestProxy) node;
    }
}
