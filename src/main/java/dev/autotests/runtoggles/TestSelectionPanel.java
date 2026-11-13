package dev.autotests.runtoggles;

import com.intellij.icons.AllIcons;
import com.intellij.ide.ActivityTracker;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.ui.CheckboxTree;
import com.intellij.ui.CheckboxTreeBase;
import com.intellij.ui.CheckboxTreeListener;
import com.intellij.ui.CheckedTreeNode;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.InplaceButton;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.fields.ExtendableTextComponent;
import com.intellij.ui.components.fields.ExtendableTextField;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.ScrollPaneConstants;
import javax.swing.UIManager;
import javax.swing.tree.TreeNode;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.FontMetrics;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

/** The right column of the PytestParams tab: the directories, modules and tests of the project with checkboxes, a
 * search (by test id or name; several, separated by comma or space); a test has the name of the case from its docstring.
 * What is ticked is what the Run button runs (see {@link SelectionCover}); it is kept in the project. */
final class TestSelectionPanel extends JPanel implements Disposable {

    /** ticking a directory or a module ticks what is in it, unticking it unticks what is in it, unticking a child unticks
     * its parents; a parent is ticked when all its children are: {@link #settleParents} (the policy does not do it) */
    private static final CheckboxTreeBase.CheckPolicy TICKS = new CheckboxTreeBase.CheckPolicy(true, true, false, true);

    /** the width of the checkbox at the left of a row: a double click on it is two clicks on the checkbox */
    private static final int CHECKBOX_WIDTH = 24;

    /** a tick moves the rows after this moment (every new tick starts it again) */
    private static final int RESORT_DELAY_MS = 200;
    private static final int REFIT_DELAY_MS = 120;

    private static final int DIR = 0;
    private static final int MODULE = 1;
    private static final int TEST = 2;

    /** doc — the name of the case (the first line of the docstring of a test), null for the rest */
    private record Item(String id, String label, int kind, String doc) {
    }

    private record Loaded(Map<String, List<String>> modules, Map<String, String> docs) {
    }

    private final Project project;
    private final RunParamsState state;
    private final ExtendableTextField search = new ExtendableTextField();
    /** the cross at the right of the search field: it is always an extension of the field, its icon is there only while
     * something is typed (nothing is added or removed while typing), a click clears the search */
    private final ExtendableTextComponent.Extension clearSearch = new ExtendableTextComponent.Extension() {
        @Override
        public Icon getIcon(boolean hovered) {
            if (search.getText().isEmpty()) {
                return EmptyIcon.ICON_16;
            }
            return hovered ? AllIcons.Actions.CloseHovered : AllIcons.Actions.Close;
        }

        @Override
        public String getTooltip() {
            return "Clear the search";
        }

        @Override
        public Runnable getActionOnClick() {
            return () -> search.setText("");
        }
    };
    private final JBLabel status = new JBLabel();
    private final CheckboxTree tree;
    private final JBScrollPane scroll;
    /** module path → test keys */
    private Map<String, List<String>> modules = Map.of();
    /** leaf id ('module::test') → the name of the case from the docstring */
    private Map<String, String> docsOf = Map.of();
    private List<SelectionCover.Node> full = List.of();
    /** the ticked leaves: the whole project, also those the search hides at the moment */
    private final Set<String> checked = new LinkedHashSet<>();
    /** the leaf nodes of the tree on the screen */
    private final List<CheckedTreeNode> leafNodes = new ArrayList<>();
    private boolean updating;
    /** a tick moves the directories and modules with ticked tests to the top: done once after the click is handled,
     * and the row that was ticked is scrolled to */
    /** the moment between a tick and the move of the rows */
    private final Timer resortTimer = new Timer(RESORT_DELAY_MS, event -> resortNow());
    /** the names of the cases are cut to the width of the tree: after the window is resized the rows are drawn again */
    private final Timer refitTimer = new Timer(REFIT_DELAY_MS, event -> refitRows());
    private int fittedWidth;

    /** the panel of the project: «Add to Sequence» in the menus of the editor and the Project view ticks tests in it */
    private static final Key<TestSelectionPanel> PANEL = Key.create("pytestParams.selectionPanel");

    TestSelectionPanel(Project project) {
        super(new BorderLayout());
        this.project = project;
        this.state = RunParamsState.getInstance(project);
        project.putUserData(PANEL, this);
        resortTimer.setRepeats(false);
        tree = new CheckboxTree(new CheckboxTree.CheckboxTreeCellRenderer() {
            @Override
            public void customizeRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf,
                                          int row, boolean hasFocus) {
                if (value instanceof CheckedTreeNode node && node.getUserObject() instanceof Item item) {
                    getTextRenderer().append(item.label());
                    appendDoc(getTextRenderer(), node, item);
                    getTextRenderer().setIcon(switch (item.kind()) {
                        case DIR -> AllIcons.Nodes.Folder;
                        case MODULE -> AllIcons.Nodes.Class;
                        default -> AllIcons.Nodes.Method;
                    });
                }
            }
        }, new CheckedTreeNode(null), TICKS);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        refitTimer.setRepeats(false);
        scroll = new JBScrollPane(tree);
        // no horizontal scrolling: what does not fit is cut with «…»
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getViewport().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                if (scroll.getViewport().getWidth() != fittedWidth) {
                    refitTimer.restart();
                }
            }
        });
        tree.addCheckboxTreeListener(new CheckboxTreeListener() {
            @Override
            public void nodeStateChanged(@NotNull CheckedTreeNode node) {
                if (!updating) {
                    syncFromTree();
                    // when the tree has finished with the click: a parent whose children are all ticked gets its tick
                    SwingUtilities.invokeLater(() -> {
                        settleParents((CheckedTreeNode) tree.getModel().getRoot());
                        tree.repaint();
                    });
                    resortAfterTick();
                }
            }
        });
        // a double click on a module or a test opens it in the editor (a click on the checkbox only ticks)
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() != 2 || event.getButton() != MouseEvent.BUTTON1) {
                    return;
                }
                TreePath path = tree.getPathForLocation(event.getX(), event.getY());
                Rectangle bounds = path == null ? null : tree.getPathBounds(path);
                if (bounds == null || event.getX() < bounds.x + JBUI.scale(CHECKBOX_WIDTH)
                        || !(path.getLastPathComponent() instanceof CheckedTreeNode node)
                        || !(node.getUserObject() instanceof Item item)) {
                    return;
                }
                openInEditor(item);
            }
        });
        search.getEmptyText().setText("Search by test id or name: 8211108, 8220829");
        search.addExtension(clearSearch);
        search.setText(state.getState().selectionSearch);
        search.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent event) {
                state.getState().selectionSearch = search.getText();
                search.repaint();
                rebuildTree();
            }
        });
        JButton clear = new JButton("Clear");
        clear.setToolTipText("Untick everything: Run then runs the counted module or the tests folder");
        clear.addActionListener(event -> {
            checked.clear();
            rebuildTree();
            persist();
        });
        // row 1: the title; at its right Clear and the reload button
        JPanel titleRow = SectionHeader.of("Tests to run", clear,
                new InplaceButton("Reload the tests of the project", AllIcons.Actions.Refresh, event -> reload()));
        // row 2: the search takes half of the column, the other half says what is ticked
        status.setBorder(JBUI.Borders.empty(0, 6));
        // a text that does not fit is cut with «…», it does not widen the column
        status.setMinimumSize(new Dimension(0, status.getMinimumSize().height));
        JPanel searchRow = new JPanel(new GridLayout(1, 2, JBUI.scale(6), 0));
        searchRow.add(search);
        searchRow.add(status);
        JPanel north = new JPanel(new BorderLayout(0, JBUI.scale(4)));
        north.add(titleRow, BorderLayout.NORTH);
        north.add(searchRow, BorderLayout.CENTER);
        north.setBorder(JBUI.Borders.empty(0, 6, 2, 6));
        add(north, BorderLayout.NORTH);
        add(SectionHeader.framed(scroll), BorderLayout.CENTER);
        updateStatus();
        reload();
    }

    @Override
    public void dispose() {
        resortTimer.stop();
        refitTimer.stop();
        if (project.getUserData(PANEL) == this) {
            project.putUserData(PANEL, null);
        }
    }

    /** «Add to Sequence»: the paths and node ids (project-relative, see {@link SelectionCover}) are added to what is
     * ticked, they are ticked in the tree, and the first of them is selected there and scrolled to. */
    static void addToSequence(Project project, List<String> ids) {
        RunParamsState state = RunParamsState.getInstance(project);
        state.getState().selected = new ArrayList<>(SelectionCover.merge(state.getState().selected, ids));
        TestSelectionPanel panel = project.getUserData(PANEL);
        if (panel != null) {
            panel.reloadTicks();
            panel.reveal(ids.get(0));
        }
        ActivityTracker.getInstance().inc();
    }

    /** The ticks follow what the project keeps (it was changed from outside the panel). */
    private void reloadTicks() {
        checked.clear();
        for (String leaf : SelectionCover.leaves(full)) {
            if (SelectionCover.covers(state.getState().selected, leaf)) {
                checked.add(leaf);
            }
        }
        rebuildTree();
    }

    /** Selects the row of the node in the tree and scrolls to it (nothing if the search hides it). */
    private void reveal(String id) {
        CheckedTreeNode node = findNode(id);
        if (node != null) {
            TreePath path = new TreePath(node.getPath());
            tree.setSelectionPath(path);
            tree.scrollPathToVisible(path);
        }
    }

    /** The node of the tree on the screen with this id, or null. */
    private CheckedTreeNode findNode(String id) {
        java.util.Enumeration<?> nodes = ((CheckedTreeNode) tree.getModel().getRoot()).preorderEnumeration();
        while (nodes.hasMoreElements()) {
            if (nodes.nextElement() instanceof CheckedTreeNode node && node.getUserObject() instanceof Item item
                    && item.id().equals(id)) {
                return node;
            }
        }
        return null;
    }

    /** The ids of the nodes that are expanded in the tree on the screen. */
    private Set<String> expandedIds() {
        Set<String> ids = new LinkedHashSet<>();
        java.util.Enumeration<?> nodes = ((CheckedTreeNode) tree.getModel().getRoot()).preorderEnumeration();
        while (nodes.hasMoreElements()) {
            if (nodes.nextElement() instanceof CheckedTreeNode node && node.getUserObject() instanceof Item item
                    && tree.isExpanded(new TreePath(node.getPath()))) {
                ids.add(item.id());
            }
        }
        return ids;
    }

    private void restoreExpanded(Set<String> ids) {
        java.util.Enumeration<?> nodes = ((CheckedTreeNode) tree.getModel().getRoot()).preorderEnumeration();
        while (nodes.hasMoreElements()) {
            if (nodes.nextElement() instanceof CheckedTreeNode node && node.getUserObject() instanceof Item item
                    && ids.contains(item.id())) {
                tree.expandPath(new TreePath(node.getPath()));
            }
        }
    }

    private String selectedId() {
        TreePath path = tree.getSelectionPath();
        return path != null && path.getLastPathComponent() instanceof CheckedTreeNode node
                && node.getUserObject() instanceof Item item ? item.id() : null;
    }

    /** After a tick: a moment later (0.2 s after the last tick) the tree is built again so that the directories, modules
     * and tests that are ticked are on top. The tree does not follow the row that moved: the scroll position and the
     * selection stay as they were. */
    private void resortAfterTick() {
        resortTimer.restart();
    }

    private void resortNow() {
        Point position = scroll.getViewport().getViewPosition();
        rebuildTree();
        keepScroll(position);
        // once more when the tree has been laid out again (its size changes with the rows)
        SwingUtilities.invokeLater(() -> keepScroll(position));
    }

    /** The view of the tree is where it was (as far as the tree is long enough). */
    private void keepScroll(Point position) {
        tree.revalidate();
        int max = Math.max(0, tree.getPreferredSize().height - scroll.getViewport().getExtentSize().height);
        scroll.getViewport().setViewPosition(new Point(position.x, Math.min(position.y, max)));
    }

    /** Reads the tests and their tags in the background (the PSI of every module) and shows them. */
    private void reload() {
        String base = project.getBasePath();
        if (base == null) {
            return;
        }
        ReadAction.nonBlocking(() -> load(base))
                .inSmartMode(project)
                .expireWith(this)
                .finishOnUiThread(ModalityState.any(), this::apply)
                .submit(AppExecutorUtil.getAppExecutorService());
    }

    private Loaded load(String base) {
        Map<String, List<String>> loadedModules = new LinkedHashMap<>();
        Map<String, String> loadedDocs = new HashMap<>();
        for (String relative : ModuleProgress.modulesOf(base)) {
            String path = new File(base, relative).getPath();
            ModuleProgress.Tests found = ModuleProgress.collect(project, path);
            List<String> tests = found.keys();
            found.docs().forEach((test, doc) -> loadedDocs.put(relative + "::" + test, doc));
            loadedModules.put(relative, tests);
        }
        return new Loaded(loadedModules, loadedDocs);
    }

    private void apply(Loaded loaded) {
        modules = loaded.modules();
        docsOf = loaded.docs();
        full = SelectionCover.tree(modules);
        checked.clear();
        for (String leaf : SelectionCover.leaves(full)) {
            if (SelectionCover.covers(state.getState().selected, leaf)) {
                checked.add(leaf);
            }
        }
        rebuildTree();
        updateStatus();
    }

    /** The tree of what the search lets through; the ticks come from {@link #checked}. */
    private void rebuildTree() {
        List<String> tokens = tokens(search.getText());
        Map<String, List<String>> shown = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> module : modules.entrySet()) {
            String path = module.getKey();
            if (module.getValue().isEmpty()) {
                if (matches(path, tokens)) {
                    shown.put(path, List.of());
                }
                continue;
            }
            List<String> keep = module.getValue().stream()
                    .filter(test -> matches(path + "::" + test, tokens))
                    .toList();
            if (!keep.isEmpty()) {
                shown.put(path, keep);
            }
        }
        Set<String> expanded = expandedIds();
        String selected = selectedId();
        boolean hadRows = ((CheckedTreeNode) tree.getModel().getRoot()).getChildCount() > 0;
        CheckedTreeNode root = new CheckedTreeNode(null);
        leafNodes.clear();
        // the directories, modules and tests that have ticks come first
        SelectionCover.selectedFirst(SelectionCover.tree(shown), checked).forEach(node -> root.add(toNode(node)));
        updating = true;
        try {
            ((DefaultTreeModel) tree.getModel()).setRoot(root);
            for (CheckedTreeNode leaf : leafNodes) {
                if (checked.contains(((Item) leaf.getUserObject()).id())) {
                    tree.setNodeState(leaf, true);
                }
            }
            settleParents(root);
            if (!tokens.isEmpty()) {
                TreeUtil.expand(tree, 4);
            } else if (!hadRows) {
                TreeUtil.expand(tree, 2);
            } else {
                restoreExpanded(expanded);
            }
            CheckedTreeNode again = selected == null ? null : findNode(selected);
            if (again != null) {
                tree.setSelectionPath(new TreePath(again.getPath()));
            }
        } finally {
            updating = false;
        }
        updateStatus();
    }

    /** After the id of a test: the name of the case in grey, cut with «…» where the row ends (the width of the tree minus
     * the indent of the row, the checkbox and the icon; what is left of the name is nothing — it is not shown). */
    private void appendDoc(ColoredTreeCellRenderer text, CheckedTreeNode node, Item item) {
        if (item.doc() == null) {
            return;
        }
        String gap = "  ";
        String shown = item.doc();
        int viewport = scroll == null ? 0 : scroll.getViewport().getWidth();
        if (viewport > 0) {
            FontMetrics metrics = text.getFontMetrics(text.getFont());
            int indent = UIManager.getInt("Tree.leftChildIndent") + UIManager.getInt("Tree.rightChildIndent");
            int room = viewport - node.getLevel() * indent - new JBCheckBox().getPreferredSize().width - JBUI.scale(22) - JBUI.scale(12)
                    - metrics.stringWidth(item.label() + gap);
            shown = TextFit.fit(shown, room, metrics::stringWidth);
        }
        if (!shown.isEmpty()) {
            text.append(gap + shown, SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }
    }

    /** The window was resized: the rows are drawn again, so that the names are cut to the new width. */
    private void refitRows() {
        fittedWidth = scroll.getViewport().getWidth();
        DefaultTreeModel model = (DefaultTreeModel) tree.getModel();
        refit(model, (TreeNode) model.getRoot());
    }

    private static void refit(DefaultTreeModel model, TreeNode parent) {
        int count = parent.getChildCount();
        if (count == 0) {
            return;
        }
        model.nodesChanged(parent, IntStream.range(0, count).toArray());
        for (int i = 0; i < count; i++) {
            refit(model, parent.getChildAt(i));
        }
    }

    private CheckedTreeNode toNode(SelectionCover.Node node) {
        String id = node.id();
        int kind = id.contains("::") ? TEST : id.endsWith(".py") ? MODULE : DIR;
        String label = kind == TEST ? id.substring(id.indexOf("::") + 2) : id.substring(id.lastIndexOf('/') + 1);
        CheckedTreeNode result = new CheckedTreeNode(new Item(id, label, kind, kind == TEST ? docsOf.get(id) : null));
        // a new node is ticked by default: every node starts unticked, the ticks come from the set of ticked leaves
        result.setChecked(false);
        node.children().forEach(child -> result.add(toNode(child)));
        if (node.leaf()) {
            leafNodes.add(result);
        }
        return result;
    }

    /** A leaf is shown when it matches one of the search words (if any): by its id or by the name of the case. */
    private boolean matches(String id, List<String> tokens) {
        String lower = (id + " " + docsOf.getOrDefault(id, "")).toLowerCase();
        return tokens.isEmpty() || tokens.stream().anyMatch(lower::contains);
    }

    private static List<String> tokens(String text) {
        List<String> out = new ArrayList<>();
        for (String token : text.toLowerCase().split("[,\\s]+")) {
            if (!token.isBlank()) {
                out.add(token);
            }
        }
        return out;
    }

    /** A parent is ticked exactly when all its children are: then its checkbox is a tick, a click on it unticks
     * everything below, and while only some children are ticked it is a minus, a click on it ticks everything. */
    private static boolean settleParents(CheckedTreeNode node) {
        if (node.getChildCount() == 0) {
            return node.isChecked();
        }
        boolean all = true;
        for (int i = 0; i < node.getChildCount(); i++) {
            all &= settleParents((CheckedTreeNode) node.getChildAt(i));
        }
        node.setChecked(all);
        return all;
    }

    /** The module or the test of the item in the editor; a directory is left to the tree (it expands). */
    private void openInEditor(Item item) {
        String base = project.getBasePath();
        if (base == null || item.kind() == DIR) {
            return;
        }
        int separator = item.id().indexOf("::");
        String module = separator < 0 ? item.id() : item.id().substring(0, separator);
        ModuleProgress.navigate(project, new File(base, module).getPath(), separator < 0 ? null : item.id().substring(separator + 2));
    }

    /** The ticks on the screen go to {@link #checked} (the hidden ones keep theirs), and to the project. */
    private void syncFromTree() {
        for (CheckedTreeNode leaf : leafNodes) {
            String id = ((Item) leaf.getUserObject()).id();
            if (leaf.isChecked()) {
                checked.add(id);
            } else {
                checked.remove(id);
            }
        }
        persist();
    }

    private void persist() {
        state.getState().selected = new ArrayList<>(SelectionCover.cover(full, checked));
        updateStatus();
        ActivityTracker.getInstance().inc();
    }

    private void updateStatus() {
        int entries = state.getState().selected.size();
        status.setText(checked.isEmpty()
                ? "Nothing ticked: Run runs the counted module or tests"
                : checked.size() + " ticked: Run runs " + entries + " path(s) / test id(s)");
        status.setToolTipText(checked.isEmpty()
                ? "Nothing is ticked: Run runs the counted module or folder, else the tests folder"
                : checked.size() + " tests are ticked: Run runs " + entries + " path(s) and test id(s)");
    }
}
