package dev.autotests.runtoggles;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunWithParamsActionTest {

    @Test
    void folderAndPytestModulesAreTargets() {
        assertTrue(RunWithParamsAction.isTestTarget("tests", true));
        assertTrue(RunWithParamsAction.isTestTarget("test_casino.py", false));
        assertTrue(RunWithParamsAction.isTestTarget("casino_test.py", false));
    }

    @Test
    void otherFilesAreNotTargets() {
        assertFalse(RunWithParamsAction.isTestTarget("conftest.py", false));
        assertFalse(RunWithParamsAction.isTestTarget("casino.py", false));
        assertFalse(RunWithParamsAction.isTestTarget("test_data.json", false));
    }
}
