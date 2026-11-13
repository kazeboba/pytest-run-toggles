package dev.autotests.runtoggles;

import com.intellij.execution.PsiLocation;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.ui.JBSplitter;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** The body of the PytestParams tab: the Run button of the toolbar, in the middle (Clean Up Module, the progress
 * counters and the reset button are on the toolbar of the Run tabs) — over two columns: the options on the left
 * (Standard, then Custom, and «+» that adds one) and the choice of what to run on the right (directories, modules,
 * tests, search, tags). */
final class PytestParamsPanel extends JPanel implements Disposable {

    PytestParamsPanel(Project project) {
        super(new BorderLayout());
        // Run alone, in the middle of the bar
        ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("PytestParamsTab",
                new DefaultActionGroup(new RunSelected(project)), true);
        toolbar.setTargetComponent(this);
        toolbar.setReservePlaceAutoPopupIcon(false);
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        bar.setBorder(JBUI.Borders.empty(2, 8, 0, 8));
        bar.add(toolbar.getComponent());
        add(bar, BorderLayout.NORTH);
        RunParamsPanel options = new RunParamsPanel(project);
        TestSelectionPanel selection = new TestSelectionPanel(project);
        Disposer.register(this, options);
        Disposer.register(this, selection);
        JBSplitter columns = new JBSplitter(false, 0.5f);
        // the columns are half and half whatever is written in them: by default the splitter gives a column the room
        // that its content asks for, so the right half changed its width when the text of the status line changed
        columns.setHonorComponentsMinimumSize(false);
        columns.setHonorComponentsPreferredSize(false);
        columns.setFirstComponent(options);
        columns.setSecondComponent(selection);
        add(columns, BorderLayout.CENTER);
    }

    @Override
    public void dispose() {
    }

    /** Runs what is ticked on the right with the options on the left: a single directory or module as itself (its
     * progress is counted, Clean Up Module works for it), several of them or tests as one run of the selection;
     * with nothing ticked — the counted module or folder, else the tests folder. The run is shown in its own tab. */
    private static final class RunSelected extends AnAction implements DumbAware {

        private final Project project;

        RunSelected(Project project) {
            super("Run", "Run with these options", AllIcons.Actions.Execute);
            this.project = project;
            getTemplatePresentation().putClientProperty(ActionUtil.SHOW_TEXT_IN_TOOLBAR, true);
        }

        static boolean canRun(Project project) {
            return !RunParamsState.getInstance(project).getState().selected.isEmpty() || fallbackTarget(project) != null;
        }

        static String hint(Project project) {
            List<String> selected = RunParamsState.getInstance(project).getState().selected;
            String fallback = fallbackTarget(project);
            return !selected.isEmpty()
                    ? "Run the ticked tests (" + selected.size() + " path(s) / test id(s)) with these options"
                    : fallback == null ? "Nothing to run: tick tests on the right"
                    : "Nothing ticked: run " + new File(fallback).getName() + " with these options";
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.EDT;
        }

        @Override
        public void update(@NotNull AnActionEvent event) {
            event.getPresentation().setEnabled(canRun(project));
            event.getPresentation().setDescription(hint(project));
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent event) {
            List<String> selected = RunParamsState.getInstance(project).getState().selected;
            String base = project.getBasePath();
            if (selected.isEmpty() || base == null) {
                runOne(fallbackTarget(project));
            } else if (selected.size() == 1 && !selected.get(0).contains("::")) {
                runOne(new File(base, selected.get(0)).getPath());
            } else {
                List<String> ids = new ArrayList<>();
                for (String entry : selected) {
                    int separator = entry.indexOf("::");
                    String path = new File(base, separator < 0 ? entry : entry.substring(0, separator)).getPath();
                    ids.add(separator < 0 ? path : path + entry.substring(separator));
                }
                PsiFileSystemItem first = ModuleProgress.psiOf(project, ids.get(0).replaceFirst("::.*$", ""));
                if (first != null) {
                    RunWithParamsAction.runSelection(project,
                            ConfigurationContext.createEmptyContextForLocation(PsiLocation.fromPsiElement(first)), ids);
                    PytestParamsTab.showRunWhenStarted(project);
                }
            }
        }

        private void runOne(String path) {
            PsiFileSystemItem item = path == null ? null : ModuleProgress.psiOf(project, path);
            if (item != null) {
                RunWithParamsAction.run(project, ConfigurationContext.createEmptyContextForLocation(PsiLocation.fromPsiElement(item)),
                        false);
                PytestParamsTab.showRunWhenStarted(project);
            }
        }

        private static String fallbackTarget(Project project) {
            return ModuleProgress.defaultTarget(RunParamsState.getInstance(project).getState().progressModule,
                    project.getBasePath());
        }
    }
}
