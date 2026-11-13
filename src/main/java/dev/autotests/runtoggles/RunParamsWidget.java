package dev.autotests.runtoggles;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.CustomStatusBarWidget;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.stream.Collectors;

/** Status bar: the included pytest options in one line; click opens the checkbox menu. */
final class RunParamsWidget implements CustomStatusBarWidget {

    static final String ID = "PytestParamsWidget";
    private static final int MAX_TEXT = 60;

    private final Project project;
    private final JBLabel label = new JBLabel();

    RunParamsWidget(Project project) {
        this.project = project;
        label.setIcon(IconLoader.getIcon("/icons/pytestParams.svg", RunParamsWidget.class));
        label.setBorder(JBUI.Borders.empty(0, 6));
        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                showPopup();
            }
        });
        project.getMessageBus().connect(this).subscribe(RunParamsTopic.TOPIC, source -> refresh());
        refresh();
    }

    private void refresh() {
        RunParamsState state = RunParamsState.getInstance(project);
        String compact = state.params().stream()
                .filter(RunParamsState.Param::included)
                .map(param -> param.hasTitle() ? param.title : String.join(" ", param.tokens()).replaceFirst("^-+", ""))
                .collect(Collectors.joining(" · "));
        label.setText(compact.isEmpty() ? "—" : StringUtil.trimMiddle(compact, MAX_TEXT));
        String args = state.arguments();
        label.setToolTipText("<html>Pytest Params (▶ runs):<br>" + StringUtil.escapeXmlEntities(args.isEmpty() ? "—" : args) + "</html>");
    }

    private void showPopup() {
        JBPopup popup = JBPopupFactory.getInstance().createActionGroupPopup("Pytest Params",
                new DefaultActionGroup(RunParamsMenu.items(project)), DataManager.getInstance().getDataContext(label),
                JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true);
        popup.show(new RelativePoint(label, new Point(0, -popup.getContent().getPreferredSize().height)));
    }

    @Override
    public JComponent getComponent() {
        return label;
    }

    @Override
    public @NotNull String ID() {
        return ID;
    }

    @Override
    public void install(@NotNull StatusBar statusBar) {
    }

    @Override
    public void dispose() {
    }
}
