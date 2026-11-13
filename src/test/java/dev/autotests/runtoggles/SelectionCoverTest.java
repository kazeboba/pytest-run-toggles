package dev.autotests.runtoggles;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectionCoverTest {

    private static List<SelectionCover.Node> tree() {
        Map<String, List<String>> modules = new LinkedHashMap<>();
        modules.put("tests/test_a.py", List.of("test_1", "test_2"));
        modules.put("tests/sub/test_b.py", List.of("test_3", "TestC::test_4"));
        modules.put("tests/sub/test_empty.py", List.of());
        return SelectionCover.tree(modules);
    }

    @Test
    void treeNestsDirectoriesModulesAndTests() {
        List<SelectionCover.Node> roots = tree();
        assertEquals(1, roots.size());
        assertEquals("tests", roots.get(0).id());
        assertEquals(Set.of("tests/test_a.py::test_1", "tests/test_a.py::test_2", "tests/sub/test_b.py::test_3",
                "tests/sub/test_b.py::TestC::test_4", "tests/sub/test_empty.py"), SelectionCover.leaves(roots));
    }

    @Test
    void everythingTickedIsOnePathOfTheTopDirectory() {
        List<SelectionCover.Node> roots = tree();
        assertEquals(List.of("tests"), SelectionCover.cover(roots, SelectionCover.leaves(roots)));
    }

    @Test
    void aWholeModuleIsItsFileTheRestAreNodeIds() {
        List<SelectionCover.Node> roots = tree();
        Set<String> ticked = Set.of("tests/test_a.py::test_1", "tests/test_a.py::test_2", "tests/sub/test_b.py::TestC::test_4");
        assertEquals(List.of("tests/sub/test_b.py::TestC::test_4", "tests/test_a.py"), SelectionCover.cover(roots, ticked));
    }

    @Test
    void aWholeSubDirectoryIsItsPath() {
        List<SelectionCover.Node> roots = tree();
        Set<String> ticked = Set.of("tests/sub/test_b.py::test_3", "tests/sub/test_b.py::TestC::test_4", "tests/sub/test_empty.py");
        assertEquals(List.of("tests/sub"), SelectionCover.cover(roots, ticked));
    }

    @Test
    void nothingTickedCoversNothing() {
        assertTrue(SelectionCover.cover(tree(), Set.of()).isEmpty());
    }

    @Test
    void aLeafIsCoveredByItsOwnIdItsModuleOrItsDirectory() {
        assertTrue(SelectionCover.covers(List.of("tests/sub"), "tests/sub/test_b.py::test_3"));
        assertTrue(SelectionCover.covers(List.of("tests/test_a.py"), "tests/test_a.py::test_1"));
        assertTrue(SelectionCover.covers(List.of("tests/test_a.py::test_1"), "tests/test_a.py::test_1"));
        assertFalse(SelectionCover.covers(List.of("tests/sub"), "tests/subway/test_x.py::test_1"));
        assertFalse(SelectionCover.covers(List.of("tests/test_a.py::test_1"), "tests/test_a.py::test_12"));
    }

    @Test
    void directoriesAndModulesWithTicksComeFirstAndTestsKeepTheirOrder() {
        List<SelectionCover.Node> ordered = SelectionCover.selectedFirst(tree(), Set.of("tests/test_a.py::test_2"));
        // 'tests' has the sub directory first and test_a.py second; test_a.py has the tick, so it goes first
        List<SelectionCover.Node> inTests = ordered.get(0).children();
        assertEquals("tests/test_a.py", inTests.get(0).id());
        assertEquals("tests/sub", inTests.get(1).id());
        // the tests of a module keep their order
        assertEquals("tests/test_a.py::test_1", inTests.get(0).children().get(0).id());
        assertEquals("tests/test_a.py::test_2", inTests.get(0).children().get(1).id());
    }

    @Test
    void withoutTicksTheOrderIsKept() {
        List<SelectionCover.Node> ordered = SelectionCover.selectedFirst(tree(), Set.of());
        assertEquals("tests/sub", ordered.get(0).children().get(0).id());
        assertEquals("tests/test_a.py", ordered.get(0).children().get(1).id());
    }

    @Test
    void addingATestThatIsAlreadyCoveredChangesNothing() {
        assertEquals(List.of("tests/test_a.py"), SelectionCover.merge(List.of("tests/test_a.py"), List.of("tests/test_a.py::test_1")));
        assertEquals(List.of("tests/sub"), SelectionCover.merge(List.of("tests/sub"), List.of("tests/sub")));
    }

    @Test
    void addingAModuleDropsItsTestsFromTheSelection() {
        List<String> merged = SelectionCover.merge(List.of("tests/test_a.py::test_1", "tests/sub/test_b.py::test_3"),
                List.of("tests/test_a.py"));
        assertEquals(List.of("tests/sub/test_b.py::test_3", "tests/test_a.py"), merged);
    }

    @Test
    void addingSeveralEntriesKeepsTheOrderOfAdding() {
        assertEquals(List.of("tests/test_a.py::test_1", "tests/sub", "tests/test_c.py"),
                SelectionCover.merge(List.of("tests/test_a.py::test_1"), List.of("tests/sub", "tests/test_c.py")));
    }

    @Test
    void aTickedTestIsNotMovedInsideItsModule() {
        List<SelectionCover.Node> ordered = SelectionCover.selectedFirst(tree(), Set.of("tests/sub/test_b.py::TestC::test_4"));
        SelectionCover.Node module = ordered.get(0).children().get(0).children().get(0);
        assertEquals("tests/sub/test_b.py", module.id());
        assertEquals(List.of("tests/sub/test_b.py::test_3", "tests/sub/test_b.py::TestC::test_4"),
                module.children().stream().map(SelectionCover.Node::id).toList());
    }
}
