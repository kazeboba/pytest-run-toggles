package dev.autotests.runtoggles;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

import javax.swing.JPanel;

/** The button on the left sidebar that opens the PytestParams tab: a tool window has no click action of its own, so
 * this one is only the button — it is shown empty for a moment and {@link RunPanelButtonListener} hides it and
 * opens the Run panel on the tab instead. It is how the tab is opened right after the IDE starts. */
public final class RunPanelButtonFactory implements ToolWindowFactory, DumbAware {

    static final String ID = "Pytest Params";

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        toolWindow.getContentManager().addContent(ContentFactory.getInstance().createContent(new JPanel(), "", false));
    }
}
