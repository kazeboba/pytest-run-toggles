package dev.autotests.runtoggles;

import com.intellij.execution.ExecutionListener;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.Executor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentDescriptorReusePolicy;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.content.ContentManagerListener;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;
import javax.swing.SwingUtilities;
import java.util.List;

/** The PytestParams tab: always in the Run panel, the first tab (the tabs of the real runs come after it), named
 * {@value #TITLE}, with the icon of the plugin. It has no process: it is the options, the choice of what to run and the
 * progress buttons. It is made when the project opens and cannot be closed. */
final class PytestParamsTab {

    static final String TITLE = "PytestParams";

    private static final Logger LOG = Logger.getInstance(PytestParamsTab.class);
    /** the component's client property: this is the tab of the plugin */
    private static final String MARK = "pytestParams.tab";
    private static final Icon ICON = IconLoader.getIcon("/icons/pytestParams.svg", PytestParamsTab.class);
    /** The IDE reuses a tab whose process is over (or that has none) for the next run of the same kind, and the new run
     * takes its place: without this the tab would be replaced by the first real run. */
    private static final RunContentDescriptorReusePolicy NEVER_REUSED = new RunContentDescriptorReusePolicy() {
        @Override
        public boolean canBeReusedBy(@NotNull RunContentDescriptor newDescriptor) {
            return false;
        }
    };
    /** when the tab was last shown by us: the Run window opened then is not the IDE's own Run button */
    private static volatile long shownAt;

    private PytestParamsTab() {
    }

    /** The Run window has just been opened by us (not by the IDE's own Run button). */
    static boolean justShown() {
        return System.currentTimeMillis() - shownAt < 2000;
    }

    static boolean isTab(RunContentDescriptor descriptor) {
        return Boolean.TRUE.equals(descriptor.getComponent().getClientProperty(MARK));
    }

    /** The index of the latest tab of a real run (the tabs are in the order they were opened); -1 — there is none.
     * ours: for every tab, whether it is the PytestParams tab. */
    static int lastRealIndex(List<Boolean> ours) {
        for (int i = ours.size() - 1; i >= 0; i--) {
            if (!ours.get(i)) {
                return i;
            }
        }
        return -1;
    }

    /** The tab, made if it is not there yet. The Run window is not opened by it (it is registered, so the Run button of
     * the IDE is on the sidebar from the start). */
    static RunContentDescriptor ensure(Project project) {
        RunContentManager contents = RunContentManager.getInstance(project);
        for (RunContentDescriptor descriptor : contents.getAllDescriptors()) {
            if (isTab(descriptor)) {
                return descriptor;
            }
        }
        PytestParamsPanel panel = new PytestParamsPanel(project);
        panel.putClientProperty(MARK, Boolean.TRUE);
        RunContentDescriptor descriptor = new RunContentDescriptor(null, null, panel, TITLE, ICON);
        descriptor.setActivateToolWindowWhenAdded(false);
        descriptor.setReusePolicy(NEVER_REUSED);
        Disposer.register(descriptor, panel);
        LOG.info("PytestParams: making the tab of " + project.getName());
        contents.showRunContent(DefaultRunExecutor.getRunExecutorInstance(), descriptor);
        pin(project, panel);
        return descriptor;
    }

    /** The Run panel with the PytestParams tab selected. */
    static void show(Project project) {
        shownAt = System.currentTimeMillis();
        Executor executor = DefaultRunExecutor.getRunExecutorInstance();
        RunContentManager contents = RunContentManager.getInstance(project);
        contents.toFrontRunContent(executor, ensure(project));
        ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(executor.getToolWindowId());
        if (window != null) {
            window.activate(null);
        }
    }

    /** The tab stays: it is pinned (a pinned tab is never taken for the next run), has no close button, and closing
     * it from the menus of the tabs is vetoed. If it is removed all the same, it is made again. */
    private static void pin(Project project, PytestParamsPanel panel) {
        ToolWindow window = ToolWindowManager.getInstance(project)
                .getToolWindow(DefaultRunExecutor.getRunExecutorInstance().getToolWindowId());
        if (window == null) {
            LOG.info("PytestParams: the Run tool window is not there, the tab is not pinned");
            return;
        }
        ContentManager contents = window.getContentManager();
        for (Content content : contents.getContents()) {
            if (content.getComponent() == panel || SwingUtilities.isDescendingFrom(panel, content.getComponent())) {
                content.setCloseable(false);
                content.setPinnable(true);
                content.setPinned(true);
                contents.addContentManagerListener(new ContentManagerListener() {
                    @Override
                    public void contentRemoveQuery(@NotNull ContentManagerEvent event) {
                        if (event.getContent() == content) {
                            event.consume();
                        }
                    }

                    @Override
                    public void contentRemoved(@NotNull ContentManagerEvent event) {
                        if (event.getContent() == content && !project.isDisposed()) {
                            LOG.warn("PytestParams: the tab was removed, making it again");
                            ApplicationManager.getApplication().invokeLater(() -> ensure(project), project.getDisposed());
                        }
                    }
                });
                return;
            }
        }
        LOG.info("PytestParams: the content of the tab was not found, the tab is not pinned");
    }

    /** The standard Run panel: when the run has started, its tab is brought to the front and the window is shown. */
    static void showRunWhenStarted(Project project) {
        Disposable once = Disposer.newDisposable("pytest-params-run");
        Disposer.register(project, once);
        project.getMessageBus().connect(once).subscribe(ExecutionManager.EXECUTION_TOPIC, new ExecutionListener() {
            @Override
            public void processStarted(@NotNull String executorId, @NotNull ExecutionEnvironment environment,
                                       @NotNull ProcessHandler handler) {
                Disposer.dispose(once);
                ApplicationManager.getApplication().invokeLater(() -> {
                    Executor executor = DefaultRunExecutor.getRunExecutorInstance();
                    RunContentManager contents = RunContentManager.getInstance(project);
                    RunContentDescriptor started = contents.findContentDescriptor(executor, handler);
                    if (started != null) {
                        contents.toFrontRunContent(executor, started);
                    }
                    ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(executor.getToolWindowId());
                    if (window != null) {
                        window.activate(null);
                    }
                }, project.getDisposed());
            }

            @Override
            public void processNotStarted(@NotNull String executorId, @NotNull ExecutionEnvironment environment) {
                Disposer.dispose(once);
            }
        });
    }
}
