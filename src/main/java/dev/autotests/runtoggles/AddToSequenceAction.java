package dev.autotests.runtoggles;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** «Add to Sequence»: puts what it is invoked on into the tests that the PytestParams tab runs — it is ticked there
 * (see {@link TestSelectionPanel}). In the editor menu it is on the name of a test, in the Project view menu — on test
 * modules and directories. */
public final class AddToSequenceAction extends AnAction implements DumbAware {

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        event.getPresentation().setEnabledAndVisible(event.getProject() != null && !ids(event).isEmpty());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        List<String> ids = ids(event);
        if (project == null || ids.isEmpty()) {
            return;
        }
        TestSelectionPanel.addToSequence(project, ids);
        Editor editor = event.getData(CommonDataKeys.EDITOR);
        if (editor != null) {
            HintManager.getInstance().showInformationHint(editor, "Added to the sequence: " + label(ids.get(0)));
        }
    }

    /** The project-relative paths or node ids the action stands for; empty — it is not for this place. */
    private static List<String> ids(AnActionEvent event) {
        Project project = event.getProject();
        String base = project == null ? null : project.getBasePath();
        if (base == null) {
            return List.of();
        }
        if (ActionPlaces.EDITOR_POPUP.equals(event.getPlace())) {
            String id = testAtCaret(event, base);
            return id == null ? List.of() : List.of(id);
        }
        VirtualFile[] files = event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        List<String> ids = new ArrayList<>();
        if (ActionPlaces.PROJECT_VIEW_POPUP.equals(event.getPlace()) && files != null) {
            for (VirtualFile file : files) {
                String relative = relative(base, file.getPath());
                if (relative != null && !relative.isEmpty() && RunWithParamsAction.isTestTarget(file.getName(), file.isDirectory())) {
                    ids.add(relative);
                }
            }
        }
        return ids;
    }

    /** 'tests/test_a.py::test_x' (or '…::TestClass::test_y') when the caret is on the name of a test; else null. */
    private static String testAtCaret(AnActionEvent event, String base) {
        Editor editor = event.getData(CommonDataKeys.EDITOR);
        PsiFile file = event.getData(CommonDataKeys.PSI_FILE);
        if (editor == null || !(file instanceof PyFile) || file.getVirtualFile() == null
                || !RunWithParamsAction.isTestTarget(file.getName(), false)) {
            return null;
        }
        int offset = editor.getCaretModel().getOffset();
        PsiElement leaf = file.findElementAt(offset);
        PyFunction function = PsiTreeUtil.getParentOfType(leaf, PyFunction.class, false);
        if (function == null || function.getNameIdentifier() == null
                || !function.getNameIdentifier().getTextRange().containsOffset(offset)
                || PsiTreeUtil.getParentOfType(function, PyFunction.class, true) != null) {
            return null;
        }
        String name = ModuleProgress.nameOf(function);
        PyClass owner = PsiTreeUtil.getParentOfType(function, PyClass.class, true);
        String ownerName = owner == null ? null : ModuleProgress.nameOf(owner);
        if (name == null || !name.startsWith("test") || owner != null && (ownerName == null || !ownerName.startsWith("Test"))) {
            return null;
        }
        String module = relative(base, file.getVirtualFile().getPath());
        return module == null ? null : module + "::" + (owner == null ? name : ownerName + "::" + name);
    }

    /** The path from the project folder with '/'; null — it is not inside the project. */
    private static String relative(String base, String path) {
        Path from = Path.of(base);
        Path to = Path.of(path);
        return to.startsWith(from) ? from.relativize(to).toString().replace(File.separatorChar, '/') : null;
    }

    private static String label(String id) {
        int separator = id.indexOf("::");
        return separator < 0 ? id : id.substring(separator + 2);
    }
}
