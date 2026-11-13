package dev.autotests.runtoggles;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** The tree of directories, modules and tests of the selection panel, and what a set of ticked tests comes to:
 * the shortest list of paths and node ids that covers exactly the ticked tests. */
final class SelectionCover {

    /** A directory ('tests/sub'), a module ('tests/sub/test_a.py') or a test ('tests/sub/test_a.py::TestC::test_x'),
     * all project-relative with '/'. A node without children is a leaf: the unit that is ticked. */
    record Node(String id, List<Node> children) {

        boolean leaf() {
            return children.isEmpty();
        }
    }

    private SelectionCover() {
    }

    /** Module path from the project (with '/') → the keys of its tests ('test_x', 'TestClass::test_y'). A module
     * without tests is a leaf itself. */
    static List<Node> tree(Map<String, List<String>> modules) {
        List<Node> roots = new ArrayList<>();
        Map<String, Node> byId = new HashMap<>();
        for (Map.Entry<String, List<String>> module : new TreeMap<>(modules).entrySet()) {
            List<Node> level = roots;
            String id = "";
            for (String part : module.getKey().split("/")) {
                id = id.isEmpty() ? part : id + "/" + part;
                Node node = byId.get(id);
                if (node == null) {
                    node = new Node(id, new ArrayList<>());
                    byId.put(id, node);
                    level.add(node);
                }
                level = node.children();
            }
            for (String test : module.getValue()) {
                level.add(new Node(module.getKey() + "::" + test, new ArrayList<>()));
            }
        }
        return roots;
    }

    /** The ids of all leaves below the nodes. */
    static Set<String> leaves(List<Node> nodes) {
        Set<String> out = new LinkedHashSet<>();
        for (Node node : nodes) {
            if (node.leaf()) {
                out.add(node.id());
            } else {
                out.addAll(leaves(node.children()));
            }
        }
        return out;
    }

    /** What to pass to pytest for the ticked leaves: a node whose leaves are all ticked stands for all of them
     * (a directory, a module), otherwise its ticked descendants are listed. */
    static List<String> cover(List<Node> nodes, Set<String> checkedLeaves) {
        List<String> out = new ArrayList<>();
        for (Node node : nodes) {
            if (allChecked(node, checkedLeaves)) {
                out.add(node.id());
            } else if (anyChecked(node, checkedLeaves)) {
                out.addAll(cover(node.children(), checkedLeaves));
            }
        }
        return out;
    }

    /** The same tree with, on every level of directories and modules, the nodes that have ticked tests first (the
     * others keep their order); the tests of a module stay in the order of the code. */
    static List<Node> selectedFirst(List<Node> nodes, Set<String> checkedLeaves) {
        if (!nodes.isEmpty() && nodes.stream().allMatch(node -> node.id().contains("::"))) {
            return nodes;
        }
        List<Node> withTicks = new ArrayList<>();
        List<Node> without = new ArrayList<>();
        for (Node node : nodes) {
            Node sorted = new Node(node.id(), new ArrayList<>(selectedFirst(node.children(), checkedLeaves)));
            (anyChecked(node, checkedLeaves) ? withTicks : without).add(sorted);
        }
        withTicks.addAll(without);
        return withTicks;
    }

    /** What the selection becomes when paths or node ids are added to it: an entry that is already covered is not
     * added, entries that the new one covers (a module over its tests) are dropped. */
    static List<String> merge(List<String> cover, List<String> added) {
        List<String> out = new ArrayList<>(cover);
        for (String id : added) {
            if (covers(out, id)) {
                continue;
            }
            out.removeIf(existing -> covers(List.of(id), existing));
            out.add(id);
        }
        return out;
    }

    /** The leaf is covered by one of the entries of a cover (the entry is the leaf itself or a path/module above it). */
    static boolean covers(Collection<String> cover, String leafId) {
        for (String entry : cover) {
            if (leafId.equals(entry) || leafId.startsWith(entry + "/") || leafId.startsWith(entry + "::")) {
                return true;
            }
        }
        return false;
    }

    private static boolean allChecked(Node node, Set<String> checked) {
        if (node.leaf()) {
            return checked.contains(node.id());
        }
        return node.children().stream().allMatch(child -> allChecked(child, checked));
    }

    private static boolean anyChecked(Node node, Set<String> checked) {
        if (node.leaf()) {
            return checked.contains(node.id());
        }
        return node.children().stream().anyMatch(child -> anyChecked(child, checked));
    }
}
