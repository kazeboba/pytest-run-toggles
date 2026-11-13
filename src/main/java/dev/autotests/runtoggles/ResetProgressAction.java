package dev.autotests.runtoggles;

import com.intellij.icons.AllIcons;
import com.intellij.ide.ActivityTracker;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/** Toolbar button next to the counters: forgets the results of the counted module or folder
 * (the next run runs all tests and the counter starts from scratch). */
final class ResetProgressAction extends AnAction implements DumbAware {

    static final ResetProgressAction INSTANCE = new ResetProgressAction();

    private ResetProgressAction() {
        super("Reset Progress", "Forget the results: the next run runs all tests", AllIcons.Actions.Rollback);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        RunParamsState.Data data = project == null ? null : RunParamsState.getInstance(project).getState();
        boolean tracked = data != null && data.progressModule != null;
        event.getPresentation().setEnabledAndVisible(tracked);
        if (tracked) {
            event.getPresentation().setDescription("Reset progress of " + new File(data.progressModule).getName()
                    + ": forget the results, the next run runs all tests");
        }
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project != null) {
            RunParamsState.getInstance(project).resetProgress();
            RunParamsSync.apply(project, this);
            ActivityTracker.getInstance().inc();
        }
    }
}
