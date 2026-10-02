package dev.autotests.runtoggles;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.StatusBarWidgetFactory;
import org.jetbrains.annotations.NotNull;

public final class RunParamsWidgetFactory implements StatusBarWidgetFactory {

    @Override
    public @NotNull String getId() {
        return RunParamsWidget.ID;
    }

    @Override
    public @NotNull String getDisplayName() {
        return "Pytest Params";
    }

    @Override
    public @NotNull StatusBarWidget createWidget(@NotNull Project project) {
        RunParamsSync.seedOnce(project);
        return new RunParamsWidget(project);
    }
}
