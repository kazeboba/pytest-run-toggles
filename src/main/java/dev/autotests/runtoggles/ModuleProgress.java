package dev.autotests.runtoggles;

import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiManager;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.run.targetBasedConfiguration.PyRunTargetVariant;
import com.jetbrains.python.testing.ConfigurationTarget;
import com.jetbrains.python.testing.PyAbstractTestConfiguration;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Progress of the counted scope — a test module (file) or a folder of modules: which run belongs to it,
 * test keys and not-run tests. Keys of a module: 'test_x', 'TestClass::test_y'; keys of a folder add the
 * module path from the folder: 'sub/test_mod.py::test_x'. */
final class ModuleProgress {

    /** pytest's default norecursedirs (besides '.*' and '*.egg') plus the site-packages of a virtualenv. */
    private static final Set<String> SKIPPED_DIRS = Set.of("__pycache__", "venv", "node_modules", "build", "dist",
            "CVS", "_darcs", "{arch}", "site-packages");

    private ModuleProgress() {
    }

    /** Scope of a run: the .py file of a module run or the folder of a folder run (PATH target);
     * null for single tests and custom targets. */
    static String scopeOf(Project project, RunConfiguration configuration) {
        PyAbstractTestConfiguration pytest = RunParamsSync.pytestLike(configuration);
        if (pytest == null) {
            return null;
        }
        ConfigurationTarget target = pytest.getTarget();
        if (target.getTargetType() != PyRunTargetVariant.PATH || target.getTarget() == null) {
            return null;
        }
        String path = target.getTarget().replace("$PROJECT_DIR$", String.valueOf(project.getBasePath()));
        File file = new File(path);
        return file.isDirectory() || file.isFile() && file.getName().endsWith(".py") ? file.getAbsolutePath() : null;
    }

    /** The scope is a folder (its progress is counted over all test modules below it). */
    static boolean isFolder(String scope) {
        return scope != null && new File(scope).isDirectory();
    }

    /** A path of a run lies in the counted folder (or is the folder itself): such a run reports to the folder. */
    static boolean isInside(String path, String folder) {
        return Path.of(path).startsWith(Path.of(folder));
    }

    /** A single test or class run (PYTHON target, 'test_mod.test_x') inside the counted module or folder. */
    static boolean isInScope(RunConfiguration configuration, String scope) {
        PyAbstractTestConfiguration pytest = RunParamsSync.pytestLike(configuration);
        if (pytest == null || scope == null) {
            return false;
        }
        ConfigurationTarget target = pytest.getTarget();
        if (target.getTargetType() != PyRunTargetVariant.PYTHON || target.getTarget() == null) {
            return false;
        }
        return isFolder(scope)
                ? folderTestKey("python<>://" + target.getTarget() + ".x", modulesOf(scope)) != null
                : ("." + target.getTarget() + ".").contains("." + stem(scope) + ".");
    }

    /** Test modules below a folder as pytest finds them by default (test_*.py, *_test.py), paths from the folder
     * with '/', sorted. */
    static List<String> modulesOf(String folder) {
        return ReadAction.compute(() -> {
            VirtualFile root = LocalFileSystem.getInstance().findFileByPath(folder);
            List<String> modules = new ArrayList<>();
            if (root != null && root.isDirectory()) {
                VfsUtilCore.visitChildrenRecursively(root, new VirtualFileVisitor<Void>() {
                    @Override
                    public boolean visitFile(VirtualFile file) {
                        if (file.isDirectory()) {
                            return file.equals(root) || !isSkippedDir(file.getName());
                        }
                        if (RunWithParamsAction.isTestTarget(file.getName(), false)) {
                            modules.add(VfsUtilCore.getRelativePath(file, root, '/'));
                        }
                        return true;
                    }
                });
            }
            Collections.sort(modules);
            return modules;
        });
    }

    /** pytest does not collect from these folders. */
    static boolean isSkippedDir(String name) {
        return name.startsWith(".") || name.endsWith(".egg") || SKIPPED_DIRS.contains(name);
    }

    /** 'python<dir>://tests.sub.test_mod.TestClass.test_x' → 'sub/test_mod.py::TestClass::test_x' for the modules of
     * a folder run; null — not a module of the folder. The longest module path wins ('sub.test_a' before 'test_a');
     * if the URL lacks the subfolders, a module name that is unique in the folder is enough. */
    static String folderTestKey(String locationUrl, List<String> modules) {
        String[] parts = urlParts(locationUrl);
        if (parts == null) {
            return null;
        }
        List<String> longestFirst = modules.stream()
                .sorted(Comparator.comparingInt((String module) -> module.split("/").length).reversed()).toList();
        for (String module : longestFirst) {
            String key = keyIn(parts, module, module.substring(0, module.length() - 3).split("/"));
            if (key != null) {
                return key;
            }
        }
        for (String module : longestFirst) {
            String stem = stem(module);
            if (modules.stream().filter(other -> stem(other).equals(stem)).count() == 1) {
                String key = keyIn(parts, module, new String[]{stem});
                if (key != null) {
                    return key;
                }
            }
        }
        return null;
    }

    /** The module (its path segments) inside the URL parts with at least the test name after it. */
    private static String keyIn(String[] parts, String module, String[] path) {
        for (int last = parts.length - 2; last >= path.length - 1; last--) {
            int first = last - path.length + 1;
            if (Arrays.equals(parts, first, last + 1, path, 0, path.length)) {
                return module + "::" + String.join("::", Arrays.copyOfRange(parts, last + 1, parts.length));
            }
        }
        return null;
    }

    /** 'python<dir>://a.b.c' → [a, b, c]; null — no location. */
    private static String[] urlParts(String locationUrl) {
        int separator = locationUrl == null ? -1 : locationUrl.indexOf("://");
        return separator < 0 ? null : locationUrl.substring(separator + 3).split("\\.");
    }

    /** 'python<dir>://pkg.test_mod.TestClass.test_x' → 'TestClass::test_x'; null — another module.
     * Matched by the module name: the dir in the URL comes from PWD and may be wrong. */
    static String testKey(String locationUrl, String module) {
        String[] parts = urlParts(locationUrl);
        if (parts == null) {
            return null;
        }
        String stem = stem(module);
        for (int i = parts.length - 2; i >= 0; i--) {
            if (parts[i].equals(stem)) {
                return String.join("::", Arrays.copyOfRange(parts, i + 1, parts.length));
            }
        }
        return null;
    }

    /** Tests of the module as pytest collects them by default: 'test_x', 'TestClass::test_y'.
     * For a folder: the tests of all its modules with the module path in front ('sub/test_mod.py::test_x'). */
    static List<String> testsOf(Project project, String module) {
        if (isFolder(module)) {
            List<String> tests = new ArrayList<>();
            for (String path : modulesOf(module)) {
                testsOf(project, new File(module, path).getPath()).forEach(test -> tests.add(path + "::" + test));
            }
            return tests;
        }
        return ReadAction.compute(() -> {
            VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(module);
            PsiFile psi = virtualFile == null ? null : PsiManager.getInstance(project).findFile(virtualFile);
            List<String> tests = new ArrayList<>();
            if (psi instanceof PyFile file) {
                for (PyFunction function : file.getTopLevelFunctions()) {
                    if (isTest(nameOf(function))) {
                        tests.add(nameOf(function));
                    }
                }
                for (PyClass testClass : file.getTopLevelClasses()) {
                    if (nameOf(testClass) != null && nameOf(testClass).startsWith("Test")) {
                        for (PyFunction method : testClass.getMethods()) {
                            if (isTest(nameOf(method))) {
                                tests.add(nameOf(testClass) + "::" + nameOf(method));
                            }
                        }
                    }
                }
            }
            return tests;
        });
    }

    /** Module tests without a result yet ('test_x[1]' counts as a result for 'test_x') and tests set to not run by hand. */
    static List<String> notRun(Project project, RunParamsState.Data data) {
        if (data.progressModule == null) {
            return List.of();
        }
        return notRun(testsOf(project, data.progressModule), data.passed, data.failed, data.pending);
    }

    static List<String> notRun(List<String> moduleTests, Collection<String> passed, Collection<String> failed,
                               Collection<String> pending) {
        List<String> results = new ArrayList<>(passed);
        results.addAll(failed);
        Set<String> notRun = new LinkedHashSet<>(moduleTests.stream()
                .filter(test -> results.stream().noneMatch(result -> result.equals(test) || result.startsWith(test + "[")))
                .toList());
        pending.stream().filter(test -> !results.contains(test)).forEach(notRun::add);
        return List.copyOf(notRun);
    }

    /** Clean up module: --deselect for passed tests. pytest deselects by node id prefix, so a passed test
     * whose id starts an unfinished test's id is kept: at worst it reruns, an unfinished test is never lost.
     * Without force (the plain panel sync) there is nothing to deselect: the permanent clean-up mode was
     * removed 2026-10-28, clean-up runs exist only as one-off runs (force=true). */
    static List<String> deselectArgs(Project project, RunParamsState.Data data) {
        return deselectArgs(project, data, false);
    }

    /** force — a one-off clean-up run: passed tests are skipped. */
    static List<String> deselectArgs(Project project, RunParamsState.Data data, boolean force) {
        if (!force || data.progressModule == null || data.passed.isEmpty() || project.getBasePath() == null) {
            return List.of();
        }
        Set<String> unfinished = new HashSet<>(notRun(project, data));
        unfinished.addAll(data.failed);
        String relative = Path.of(project.getBasePath()).relativize(Path.of(data.progressModule)).toString()
                .replace(File.separatorChar, '/');
        return deselectArgs(data.passed, unfinished, nodePrefix(relative, isFolder(data.progressModule)));
    }

    /** Node id start of the scope's tests: 'tests/test_x.py::' for a module, 'tests/' for a folder
     * (its keys carry the module); the project folder itself needs no prefix. */
    static String nodePrefix(String relativePath, boolean folder) {
        if (!folder) {
            return relativePath + "::";
        }
        return relativePath.isEmpty() ? "" : relativePath + "/";
    }

    static List<String> deselectArgs(Collection<String> passed, Collection<String> unfinished, String nodePrefix) {
        List<String> args = new ArrayList<>();
        for (String test : passed) {
            if (unfinished.stream().noneMatch(other -> other.startsWith(test))) {
                args.add("--deselect=" + nodePrefix + test);
            }
        }
        return args;
    }

    /** Every test has passed: a clean-up run would deselect all of them and collect nothing. */
    static boolean allPassed(Collection<String> passed, Collection<String> failed, Collection<String> notRun) {
        return !passed.isEmpty() && failed.isEmpty() && notRun.isEmpty();
    }

    static String stem(String module) {
        String name = new File(module).getName();
        return name.endsWith(".py") ? name.substring(0, name.length() - 3) : name;
    }

    /** Through PsiNamedElement: PyFunction/PyClass.getName() resolves to the experimental PyAst* interfaces. */
    private static String nameOf(PsiNamedElement element) {
        return element.getName();
    }

    private static boolean isTest(String name) {
        return name != null && name.startsWith("test");
    }
}
