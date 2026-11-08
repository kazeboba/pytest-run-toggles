package dev.autotests.runtoggles;

import com.intellij.execution.PsiLocation;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;
import java.io.File;

/** Run tab toolbar (left of the module counter): a one-off clean-up run — only failed and not run tests of
 * the counted module or folder, passed tests are skipped. In the project tree context menu the broom shows on every
 * test module and folder: on the counted one it runs its clean-up, on another one it switches the counter to that
 * module or folder (progress starts from scratch) and runs it. */
final class ModuleCleanupAction extends AnAction implements DumbAware {

    // ICON goes first: the constructor of INSTANCE reads it, a later field would still be null there
    private static final Icon ICON = IconLoader.getIcon("/icons/cleanupModule.svg", ModuleCleanupAction.class);

    static final ModuleCleanupAction INSTANCE = new ModuleCleanupAction();

    // public: plugin.xml creates the context menu instance reflectively
    public ModuleCleanupAction() {
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
        if (data == null) {
            event.getPresentation().setEnabledAndVisible(false);
            return;
        }
        // the context menu broom shows on every test module; switching to another module is always possible
        VirtualFile clicked = contextMenuModule(event);
        if (clicked != null) {
            event.getPresentation().setEnabledAndVisible(true);
            if (clicked.getPath().equals(data.progressModule)) {
                setCountedModulePresentation(event, project, data);
            } else {
                event.getPresentation().setDescription("Switch the module counter to " + clicked.getName()
                        + " (progress starts from scratch) and run it");
            }
            return;
        }
        // Run tab toolbar: the counted module only
        boolean tracked = data.progressModule != null;
        event.getPresentation().setVisible(tracked);
        if (!tracked) {
            event.getPresentation().setEnabled(false);
            return;
        }
        setCountedModulePresentation(event, project, data);
    }

    /** The counted module: enabled while there is something to clean up. */
    private static void setCountedModulePresentation(AnActionEvent event, Project project, RunParamsState.Data data) {
        String module = new File(data.progressModule).getName();
        boolean allPassed = ModuleProgress.allPassed(data.passed, data.failed, ModuleProgress.notRun(project, data));
        event.getPresentation().setEnabled(!allPassed);
        event.getPresentation().setDescription(allPassed
                ? module + ": all tests passed, nothing to clean up"
                : "Clean up module: run only failed and not run tests of " + module + " (passed tests are skipped)");
    }

    /** The clicked test module ('test_*.py') or folder when the action sits in the project tree context menu, else null. */
    private static VirtualFile contextMenuModule(AnActionEvent event) {
        if (!ActionPlaces.PROJECT_VIEW_POPUP.equals(event.getPlace())) {
            return null;
        }
        VirtualFile clicked = event.getData(CommonDataKeys.VIRTUAL_FILE);
        boolean testTarget = clicked != null && RunWithParamsAction.isTestTarget(clicked.getName(), clicked.isDirectory());
        return testTarget ? clicked : null;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) {
            return;
        }
        RunParamsState state = RunParamsState.getInstance(project);
        VirtualFile clicked = contextMenuModule(event);
        if (clicked != null && !clicked.getPath().equals(state.getState().progressModule)) {
            // the broom on another module or folder: the counter switches to it, its progress starts from scratch
            state.trackModule(clicked.getPath());
            RunParamsSync.apply(project, this);
        }
        String module = state.getState().progressModule;
        if (module == null) {
            return;
        }
        PsiFileSystemItem target = ReadAction.compute(() -> {
            VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(module);
            if (virtualFile == null) {
                return null;
            }
            return virtualFile.isDirectory() ? PsiManager.getInstance(project).findDirectory(virtualFile)
                    : PsiManager.getInstance(project).findFile(virtualFile);
        });
        if (target != null) {
            RunWithParamsAction.run(project, ConfigurationContext.createEmptyContextForLocation(PsiLocation.fromPsiElement(target)),
                    true);
        }
    }
}
