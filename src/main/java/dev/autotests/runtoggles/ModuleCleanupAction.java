package dev.autotests.runtoggles;

import com.intellij.execution.PsiLocation;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiFileSystemItem;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;
import java.io.File;

/** Run tab toolbar (left of the module counter): a one-off clean-up run — only failed and not run tests of
 * the counted module or folder, passed tests are skipped. */
final class ModuleCleanupAction extends AnAction implements DumbAware {

    // ICON goes first: the constructor of INSTANCE reads it, a later field would still be null there
    private static final Icon ICON = IconLoader.getIcon("/icons/cleanupModule.svg", ModuleCleanupAction.class);

    static final ModuleCleanupAction INSTANCE = new ModuleCleanupAction();

    private ModuleCleanupAction() {
        super("Clean Up Module", null, ICON);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        RunParamsState.Data data = project == null ? null : RunParamsState.getInstance(project).getState();
        // the counted module or folder only
        boolean tracked = data != null && data.progressModule != null;
        event.getPresentation().setVisible(tracked);
        if (!tracked) {
            event.getPresentation().setEnabled(false);
            return;
        }
        String module = new File(data.progressModule).getName();
        boolean allPassed = ModuleProgress.allPassed(data.passed, data.failed, ModuleProgress.notRun(project, data));
        event.getPresentation().setEnabled(!allPassed);
        event.getPresentation().setDescription(allPassed
                ? module + ": all tests passed, nothing to clean up"
                : "Clean up module: run only failed and not run tests of " + module + " (passed tests are skipped)");
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        String module = project == null ? null : RunParamsState.getInstance(project).getState().progressModule;
        if (module == null) {
            return;
        }
        PsiFileSystemItem target = ModuleProgress.psiOf(project, module);
        if (target != null) {
            RunWithParamsAction.run(project, ConfigurationContext.createEmptyContextForLocation(PsiLocation.fromPsiElement(target)),
                    true);
        }
    }
}
