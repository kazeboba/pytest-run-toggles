package dev.autotests.runtoggles;

import com.intellij.ide.ActivityTracker;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.KeepPopupOnPerform;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Ref;
import com.intellij.ui.popup.AbstractPopup;
import org.jetbrains.annotations.NotNull;

import javax.swing.SwingConstants;
import java.awt.Component;
import java.awt.event.InputEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Run tab toolbar: '✔ 18  ✖ 2  ○ 5' — passed, failed and not run tests of the module or folder. Click opens the tests:
 * a click moves a test to the next status, ⌘/Ctrl-click opens it (the progress is reset with its own button,
 * {@link ResetProgressAction}).
 * (The permanent 'Clean up module' toggle was removed 2026-10-28: it duplicated the one-off
 * clean-up run button of the toolbar; the one-off button is the only clean-up entry point now.) */
final class ModuleStatsAction extends AnAction implements DumbAware {

    static final ModuleStatsAction INSTANCE = new ModuleStatsAction();

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }

    private ModuleStatsAction() {
        getTemplatePresentation().putClientProperty(ActionUtil.SHOW_TEXT_IN_TOOLBAR, true);
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        RunParamsState.Data data = project == null ? null : RunParamsState.getInstance(project).getState();
        boolean visible = data != null && data.progressModule != null;
        event.getPresentation().setEnabledAndVisible(visible);
        if (visible) {
            int notRun = ModuleProgress.notRun(project, data).size();
            event.getPresentation().setText("✔ " + data.passed.size()
                    + "  ✖ " + data.failed.size() + "  ○ " + notRun, false);
            event.getPresentation().setDescription(new File(data.progressModule).getName()
                    + ": passed · failed · not run. Click for the test list");
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
        Ref<JBPopup> popupRef = new Ref<>();
        DefaultActionGroup menu = new DefaultActionGroup();
        // the rows keep their place while statuses change; the order is rebuilt when the list is opened again
        List<String> failed = new ArrayList<>(data.failed);
        List<String> notRun = ModuleProgress.notRun(project, data);
        List<String> passed = new ArrayList<>(data.passed);
        Stream.of(failed, notRun, passed).flatMap(List::stream)
                .forEach(test -> menu.add(new TestRow(test, module, popupRef)));
        // short title (module name); the click hints go to the grey ad line at the popup bottom
        JBPopup popup = JBPopupFactory.getInstance().createActionGroupPopup(
                new File(module).getName() + " — progress", menu,
                event.getDataContext(), JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true);
        if (popup instanceof AbstractPopup abstractPopup) {
            abstractPopup.setAdText("Click — next status · ⌘/Ctrl-click — open the test", SwingConstants.LEFT);
        }
        popupRef.set(popup);
        Component anchor = event.getInputEvent() == null ? null : event.getInputEvent().getComponent();
        if (anchor != null) {
            popup.showUnderneathOf(anchor);
        } else {
            popup.showInBestPositionFor(event.getDataContext());
        }
    }

    /** A test of the module: click → next status (✔ passed → ○ not run → ✖ failed → ✔), ⌘/Ctrl-click → opens the test.
     * The list stays open, so several tests can be switched in a row. */
    private static final class TestRow extends AnAction implements DumbAware {

        private final String test;
        private final String module;
        private final Ref<JBPopup> popup;

        TestRow(String test, String module, Ref<JBPopup> popup) {
            this.test = test;
            this.module = module;
            this.popup = popup;
            getTemplatePresentation().setText(test, false);
            getTemplatePresentation().setKeepPopupOnPerform(KeepPopupOnPerform.Always);
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.EDT;
        }

        @Override
        public void update(@NotNull AnActionEvent event) {
            Project project = event.getProject();
            TestStatus status = project == null ? TestStatus.NOT_RUN
                    : TestStatus.of(RunParamsState.getInstance(project).getState(), test);
            Presentation presentation = event.getPresentation();
            presentation.setIcon(StatusIcon.of(status));
            presentation.setText(test, false);
            presentation.setKeepPopupOnPerform(KeepPopupOnPerform.Always);
            presentation.setDescription(switch (status) {
                case PASSED -> "Passed: skipped by Clean up module. Click: not run (joins the rerun)";
                case NOT_RUN -> "Not run: runs in Clean up module. Click: failed";
                case FAILED -> "Failed: runs in Clean up module. Click: passed (leaves the rerun)";
            });
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent event) {
            Project project = event.getProject();
            if (project == null) {
                return;
            }
            InputEvent input = event.getInputEvent();
            if (input != null && (input.isMetaDown() || input.isControlDown())) {
                if (popup.get() != null) {
                    popup.get().cancel();
                }
                open(project);
                return;
            }
            RunParamsState state = RunParamsState.getInstance(project);
            state.setStatus(test, TestStatus.of(state.getState(), test).next());
            // --deselect of the clean-up mode and the counters follow the new status
            RunParamsSync.apply(project, this);
            ActivityTracker.getInstance().inc();
        }

        /** 'test_x', 'TestClass::test_y', 'test_z[1]' → the test function in the module file;
         * in a folder the key starts with the module path ('sub/test_mod.py::test_x'). */
        private void open(Project project) {
            String modulePath = module;
            String name = test;
            if (ModuleProgress.isFolder(module)) {
                int separator = test.indexOf("::");
                if (separator < 0) {
                    return;
                }
                modulePath = new File(module, test.substring(0, separator)).getPath();
                name = test.substring(separator + 2);
            }
            ModuleProgress.navigate(project, modulePath, name);
        }
    }
}
