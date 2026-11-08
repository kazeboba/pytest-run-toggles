package dev.autotests.runtoggles;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModuleProgressTest {

    private static final String MODULE = "/p/tests/test_alpha.py";

    @Test
    void testKeyFromPycharmLocation() {
        assertEquals("test_case_1", ModuleProgress.testKey("python</p>://tests.test_alpha.test_case_1", MODULE));
        assertEquals("TestGroup::test_b", ModuleProgress.testKey("python</p>://tests.test_alpha.TestGroup.test_b", MODULE));
        assertEquals("test_param[2]", ModuleProgress.testKey("python</p>://tests.test_alpha.test_param[2]", MODULE));
    }

    @Test
    void testKeyIgnoresTheDirectoryFromPwd() {
        assertEquals("test_case_1", ModuleProgress.testKey("python</>://tests.test_alpha.test_case_1", MODULE));
    }

    @Test
    void testKeyOfAnotherModuleOrBadUrlIsNull() {
        assertNull(ModuleProgress.testKey("python</p>://tests.test_beta.test_case_1", MODULE));
        assertNull(ModuleProgress.testKey("no-separator", MODULE));
        assertNull(ModuleProgress.testKey(null, MODULE));
    }

    private static final List<String> FOLDER = List.of("test_alpha.py", "test_beta.py", "sub/test_alpha.py", "sub/deep/test_gamma.py");

    @Test
    void folderTestKeyCarriesTheModulePath() {
        assertEquals("test_beta.py::test_case_1", ModuleProgress.folderTestKey("python</p>://tests.test_beta.test_case_1", FOLDER));
        assertEquals("test_beta.py::TestGroup::test_b",
                ModuleProgress.folderTestKey("python</p>://tests.test_beta.TestGroup.test_b", FOLDER));
        assertEquals("sub/deep/test_gamma.py::test_param[2]",
                ModuleProgress.folderTestKey("python</p>://tests.sub.deep.test_gamma.test_param[2]", FOLDER));
    }

    @Test
    void folderTestKeyOfSameNamedModulesIsToldApartBySubfolder() {
        assertEquals("sub/test_alpha.py::test_x", ModuleProgress.folderTestKey("python</p>://tests.sub.test_alpha.test_x", FOLDER));
        assertEquals("test_alpha.py::test_x", ModuleProgress.folderTestKey("python</p>://tests.test_alpha.test_x", FOLDER));
    }

    @Test
    void folderTestKeyNeedsOnlyAUniqueModuleNameWhenTheUrlLacksSubfolders() {
        assertEquals("sub/deep/test_gamma.py::test_x", ModuleProgress.folderTestKey("python</p>://test_gamma.test_x", FOLDER));
        // a module name that exists twice can't be told without the subfolder
        assertNull(ModuleProgress.folderTestKey("python</p>://test_alpha.test_x", List.of("a/test_alpha.py", "b/test_alpha.py")));
    }

    @Test
    void folderTestKeyOfAnotherModuleOrBadUrlIsNull() {
        assertNull(ModuleProgress.folderTestKey("python</p>://tests.test_other.test_x", FOLDER));
        assertNull(ModuleProgress.folderTestKey("python</p>://tests.test_beta", FOLDER));
        assertNull(ModuleProgress.folderTestKey("no-separator", FOLDER));
        assertNull(ModuleProgress.folderTestKey(null, FOLDER));
    }

    @Test
    void pathInsideTheCountedFolderReportsToIt() {
        assertTrue(ModuleProgress.isInside("/p/tests/test_alpha.py", "/p/tests"));
        assertTrue(ModuleProgress.isInside("/p/tests/sub", "/p/tests"));
        assertTrue(ModuleProgress.isInside("/p/tests", "/p/tests"));
        assertFalse(ModuleProgress.isInside("/p/tests_old/test_alpha.py", "/p/tests"));
        assertFalse(ModuleProgress.isInside("/p", "/p/tests"));
    }

    @Test
    void nodePrefixOfAFolderEndsWithASlashOfAModuleWithColons() {
        assertEquals("tests/test_alpha.py::", ModuleProgress.nodePrefix("tests/test_alpha.py", false));
        assertEquals("tests/", ModuleProgress.nodePrefix("tests", true));
        assertEquals("", ModuleProgress.nodePrefix("", true));
    }

    @Test
    void folderDeselectUsesTheFolderKeys() {
        List<String> args = ModuleProgress.deselectArgs(List.of("test_beta.py::test_1", "sub/test_alpha.py::TestG::test_2"),
                List.of("test_beta.py::test_12"), ModuleProgress.nodePrefix("tests", true));
        assertEquals(List.of("--deselect=tests/sub/test_alpha.py::TestG::test_2"), args);
    }

    @Test
    void pytestDoesNotCollectFromTheseFolders() {
        assertTrue(ModuleProgress.isSkippedDir("__pycache__"));
        assertTrue(ModuleProgress.isSkippedDir(".git"));
        assertTrue(ModuleProgress.isSkippedDir("venv"));
        assertTrue(ModuleProgress.isSkippedDir("pkg.egg"));
        assertFalse(ModuleProgress.isSkippedDir("pages"));
        assertFalse(ModuleProgress.isSkippedDir("sub"));
    }

    @Test
    void deselectKeepsPassedTestThatPrefixesAnUnfinishedOne() {
        List<String> args = ModuleProgress.deselectArgs(List.of("test_case_1", "test_alpha_1"),
                List.of("test_case_12"), "tests/test_alpha.py::");
        assertEquals(List.of("--deselect=tests/test_alpha.py::test_alpha_1"), args);
    }

    @Test
    void deselectParametrizedIdsAreExact() {
        List<String> args = ModuleProgress.deselectArgs(List.of("test_param[1]"), List.of("test_param[10]"),
                "tests/test_alpha.py::");
        assertEquals(List.of("--deselect=tests/test_alpha.py::test_param[1]"), args);
    }

    @Test
    void stemDropsPyExtension() {
        assertEquals("test_alpha", ModuleProgress.stem(MODULE));
    }

    @Test
    void allPassedMeansNothingLeftForCleanUp() {
        assertTrue(ModuleProgress.allPassed(List.of("test_a", "test_b"), List.of(), List.of()));
    }

    @Test
    void failedOrNotRunTestsLeaveWorkForCleanUp() {
        assertFalse(ModuleProgress.allPassed(List.of("test_a"), List.of("test_b"), List.of()));
        assertFalse(ModuleProgress.allPassed(List.of("test_a"), List.of(), List.of("test_c")));
    }

    @Test
    void noResultsYetIsNotAllPassed() {
        assertFalse(ModuleProgress.allPassed(List.of(), List.of(), List.of()));
    }

    @Test
    void notRunHasModuleTestsWithoutResultAndHandStatuses() {
        List<String> notRun = ModuleProgress.notRun(List.of("test_a", "test_b", "test_p", "test_c"),
                List.of("test_a", "test_p[1]"), List.of("test_c"), List.of("test_p[2]", "test_b"));
        assertEquals(List.of("test_b", "test_p[2]"), notRun);
    }

    @Test
    void notRunDoesNotListAPendingTestThatHasAResult() {
        List<String> notRun = ModuleProgress.notRun(List.of("test_a"), List.of("test_a"), List.of(), List.of("test_a"));
        assertEquals(List.of(), notRun);
    }
}
