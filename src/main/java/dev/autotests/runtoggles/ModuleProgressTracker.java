package dev.autotests.runtoggles;

import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.sm.runner.SMTRunnerEventsAdapter;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Counts test results of a module or a folder: a run of another module or folder switches the counted scope and
 * starts from scratch; runs inside the counted folder (its modules, subfolders, single tests) report to it.
 * Single tests of other modules are ignored. Skipped tests stay not run. */
public final class ModuleProgressTracker extends SMTRunnerEventsAdapter {

    /** The scope a run reports to; modules — the test modules of a folder scope (null for a module). */
    private record Run(String scope, List<String> modules) {

        static Run of(String scope) {
            return new Run(scope, ModuleProgress.isFolder(scope) ? ModuleProgress.modulesOf(scope) : null);
        }

        String keyOf(String locationUrl) {
            return modules == null ? ModuleProgress.testKey(locationUrl, scope)
                    : ModuleProgress.folderTestKey(locationUrl, modules);
        }
    }

    private final Project project;
    private final Map<SMTestProxy.SMRootTestProxy, Run> runs = Collections.synchronizedMap(new WeakHashMap<>());

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
        String counted = state.getState().progressModule;
        String target = ModuleProgress.scopeOf(project, configuration);
        if (target != null) {
            // a module or a subfolder of the counted folder is a part of its progress, anything else is a new scope
            String scope = ModuleProgress.isFolder(counted) && ModuleProgress.isInside(target, counted) ? counted : target;
            state.trackModule(scope);
            runs.put(root, Run.of(scope));
        } else if (ModuleProgress.isInScope(configuration, counted)) {
            runs.put(root, Run.of(counted));
        }
    }

    @Override
    public void onTestFinished(@NotNull SMTestProxy test) {
        Run run = test.isLeaf() ? runs.get(rootOf(test)) : null;
        String key = run == null ? null : run.keyOf(test.getLocationUrl());
        if (key == null) {
            return;
        }
        if (test.isDefect()) {
            RunParamsState.getInstance(project).record(key, false);
        } else if (test.isPassed()) {
            RunParamsState.getInstance(project).record(key, true);
        }
    }

    @Override
    public void onTestingFinished(@NotNull SMTestProxy.SMRootTestProxy root) {
        // the permanent clean-up mode is gone (one-off runs rebuild --deselect themselves) —
        // nothing to refresh, only the run bookkeeping remains
        runs.remove(root);
    }

    private static SMTestProxy.SMRootTestProxy rootOf(SMTestProxy test) {
        AbstractTestProxy node = test;
        while (node != null && !(node instanceof SMTestProxy.SMRootTestProxy)) {
            node = node.getParent();
        }
        return (SMTestProxy.SMRootTestProxy) node;
    }
}
