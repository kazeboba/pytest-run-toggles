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
