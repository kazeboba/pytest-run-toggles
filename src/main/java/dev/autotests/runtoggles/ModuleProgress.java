package dev.autotests.runtoggles;

import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.run.targetBasedConfiguration.PyRunTargetVariant;
import com.jetbrains.python.testing.ConfigurationTarget;
import com.jetbrains.python.testing.PyTestConfiguration;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Progress of the counted test module: which run belongs to it, test keys and not-run tests. */
final class ModuleProgress {

    private ModuleProgress() {
    }

    /** Module file of a module run (PATH target to a .py file); null for folders, single tests and custom targets. */
    static String moduleOf(Project project, RunConfiguration configuration) {
        if (!(configuration instanceof PyTestConfiguration pytest)) {
            return null;
        }
        ConfigurationTarget target = pytest.getTarget();
        if (target.getTargetType() != PyRunTargetVariant.PATH || target.getTarget() == null) {
            return null;
        }
        String path = target.getTarget().replace("$PROJECT_DIR$", String.valueOf(project.getBasePath()));
        File file = new File(path);
        return file.isFile() && file.getName().endsWith(".py") ? file.getAbsolutePath() : null;
    }

    /** A single test or class run (PYTHON target, 'test_mod.test_x') inside the module. */
    static boolean isInModule(RunConfiguration configuration, String module) {
        if (!(configuration instanceof PyTestConfiguration pytest) || module == null) {
            return false;
        }
        ConfigurationTarget target = pytest.getTarget();
        return target.getTargetType() == PyRunTargetVariant.PYTHON && target.getTarget() != null
                && ("." + target.getTarget() + ".").contains("." + stem(module) + ".");
    }

    /** 'python<dir>://pkg.test_mod.TestClass.test_x' → 'TestClass::test_x'; null — another module.
     * Matched by the module name: the dir in the URL comes from PWD and may be wrong. */
    static String testKey(String locationUrl, String module) {
        int separator = locationUrl == null ? -1 : locationUrl.indexOf("://");
        if (separator < 0) {
            return null;
        }
        String[] parts = locationUrl.substring(separator + 3).split("\\.");
        String stem = stem(module);
        for (int i = parts.length - 2; i >= 0; i--) {
            if (parts[i].equals(stem)) {
                return String.join("::", Arrays.copyOfRange(parts, i + 1, parts.length));
            }
        }
        return null;
    }

    /** Tests of the module as pytest collects them by default: 'test_x', 'TestClass::test_y'. */
    static List<String> testsOf(Project project, String module) {
        return ReadAction.compute(() -> {
            VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(module);
            PsiFile psi = virtualFile == null ? null : PsiManager.getInstance(project).findFile(virtualFile);
            List<String> tests = new ArrayList<>();
            if (psi instanceof PyFile file) {
                for (PyFunction function : file.getTopLevelFunctions()) {
                    if (isTest(function.getName())) {
                        tests.add(function.getName());
                    }
                }
                for (PyClass testClass : file.getTopLevelClasses()) {
                    if (testClass.getName() != null && testClass.getName().startsWith("Test")) {
                        for (PyFunction method : testClass.getMethods()) {
                            if (isTest(method.getName())) {
                                tests.add(testClass.getName() + "::" + method.getName());
                            }
                        }
                    }
                }
            }
            return tests;
        });
    }

    /** Module tests without a result yet ('test_x[1]' counts as a result for 'test_x'). */
    static List<String> notRun(Project project, RunParamsState.Data data) {
        if (data.progressModule == null) {
            return List.of();
        }
        List<String> results = new ArrayList<>(data.passed);
        results.addAll(data.failed);
        return testsOf(project, data.progressModule).stream()
                .filter(test -> results.stream().noneMatch(result -> result.equals(test) || result.startsWith(test + "[")))
                .toList();
    }

    /** Clean up module: --deselect for passed tests. pytest deselects by node id prefix, so a passed test
     * whose id starts an unfinished test's id is kept: at worst it reruns, an unfinished test is never lost. */
    static List<String> deselectArgs(Project project, RunParamsState.Data data) {
        if (!data.cleanup || data.progressModule == null || data.passed.isEmpty() || project.getBasePath() == null) {
            return List.of();
        }
        Set<String> unfinished = new HashSet<>(notRun(project, data));
        unfinished.addAll(data.failed);
        String nodePrefix = Path.of(project.getBasePath()).relativize(Path.of(data.progressModule)).toString()
                .replace(File.separatorChar, '/') + "::";
        List<String> args = new ArrayList<>();
        for (String passed : data.passed) {
            if (unfinished.stream().noneMatch(other -> other.startsWith(passed))) {
                args.add("--deselect=" + nodePrefix + passed);
            }
        }
        return args;
    }

    static String stem(String module) {
        String name = new File(module).getName();
        return name.endsWith(".py") ? name.substring(0, name.length() - 3) : name;
    }

    private static boolean isTest(String name) {
        return name != null && name.startsWith("test");
    }
}
