package dev.autotests.runtoggles;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.NotNull;

import java.awt.Component;
import java.io.File;

/** Run tab toolbar: '✔ 18  ✖ 2  ○ 5' — passed, failed and not run tests of the module. Click opens a menu:
 * Clean up module toggle, Reset progress and the tests (choosing one opens it). */
final class ModuleStatsAction extends AnAction implements DumbAware {

    static final ModuleStatsAction INSTANCE = new ModuleStatsAction();

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }

    @Override
    public boolean displayTextInToolbar() {
        return true;
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        RunParamsState.Data data = project == null ? null : RunParamsState.getInstance(project).getState();
        boolean visible = data != null && data.progressModule != null;
        event.getPresentation().setEnabledAndVisible(visible);
        if (visible) {
            int notRun = ModuleProgress.notRun(project, data).size();
            event.getPresentation().setText((data.cleanup ? "clean-up  " : "") + "✔ " + data.passed.size()
                    + "  ✖ " + data.failed.size() + "  ○ " + notRun, false);
            event.getPresentation().setDescription(new File(data.progressModule).getName()
                    + ": passed · failed · not run. Click for clean-up and the test list");
        }
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) {
            return;
        }
        RunParamsState.Data data = RunParamsState.getInstance(project).getState();
        String module = data.progressModule;
        DefaultActionGroup menu = new DefaultActionGroup();
        menu.add(new CleanupToggle());
        menu.add(new ResetProgress());
        menu.add(new Separator(new File(module).getName()));
        data.failed.forEach(test -> menu.add(new OpenTest("✖ ", test, module)));
        ModuleProgress.notRun(project, data).forEach(test -> menu.add(new OpenTest("○ ", test, module)));
        data.passed.forEach(test -> menu.add(new OpenTest("✔ ", test, module)));
        JBPopup popup = JBPopupFactory.getInstance().createActionGroupPopup("Module progress", menu,
                event.getDataContext(), JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true);
        Component anchor = event.getInputEvent() == null ? null : event.getInputEvent().getComponent();
        if (anchor != null) {
            popup.showUnderneathOf(anchor);
        } else {
            popup.showInBestPositionFor(event.getDataContext());
        }
    }

    /** Clean up module: runs of the module skip tests that already passed, so only failed and not run tests run. */
    private static final class CleanupToggle extends ToggleAction implements DumbAware {

        CleanupToggle() {
            super("Clean up module", "Module runs skip tests that already passed: only failed and not run tests run", null);
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.EDT;
        }

        @Override
        public boolean isSelected(@NotNull AnActionEvent event) {
            return event.getProject() != null && RunParamsState.getInstance(event.getProject()).getState().cleanup;
        }

        @Override
        public void setSelected(@NotNull AnActionEvent event, boolean state) {
            if (event.getProject() != null) {
                RunParamsState.getInstance(event.getProject()).getState().cleanup = state;
                RunParamsSync.apply(event.getProject(), this);
            }
        }
    }

    private static final class ResetProgress extends AnAction implements DumbAware {

        ResetProgress() {
            super("Reset progress", "Forget the results: the next module run runs all tests", AllIcons.Actions.Rollback);
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.EDT;
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent event) {
            if (event.getProject() != null) {
                RunParamsState.getInstance(event.getProject()).resetProgress();
                RunParamsSync.apply(event.getProject(), this);
            }
        }
    }

    /** '✖ test_x' → opens the test function in the module file. */
    private static final class OpenTest extends AnAction implements DumbAware {

        private final String test;
        private final String module;

        OpenTest(String mark, String test, String module) {
            this.test = test;
            this.module = module;
            getTemplatePresentation().setText(mark + test, false);
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.BGT;
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent event) {
            Project project = event.getProject();
            if (project == null) {
                return;
            }
            String[] path = test.replaceAll("\\[.*$", "").split("::");
            PyFunction function = ReadAction.compute(() -> {
                VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(module);
                PsiFile psi = virtualFile == null ? null : PsiManager.getInstance(project).findFile(virtualFile);
                if (!(psi instanceof PyFile file)) {
                    return null;
                }
                if (path.length == 1) {
                    return file.findTopLevelFunction(path[0]);
                }
                PyClass testClass = file.findTopLevelClass(path[0]);
                return testClass == null ? null : testClass.findMethodByName(path[1], false, null);
            });
            if (function != null) {
                function.navigate(true);
            }
        }
    }
}
