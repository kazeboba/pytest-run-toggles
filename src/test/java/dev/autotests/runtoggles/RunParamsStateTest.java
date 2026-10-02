package dev.autotests.runtoggles;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunParamsStateTest {

    @Test
    void parsesOptionsWithValuesFlagsAndQuotedSpaces() {
        List<RunParamsState.Param> params = RunParamsState.parse("--debugging=0 -k \"smoke a\" -x --tb=short -p no:warnings", false);
        assertEquals(5, params.size());
        assertEquals("--debugging", params.get(0).name);
        assertEquals("0", params.get(0).value);
        assertEquals("smoke a", params.get(1).value);
        assertNull(params.get(2).value);
        assertEquals("short", params.get(3).value);
        assertEquals("no:warnings", params.get(4).value);
    }

    @Test
    void argumentsPutStandardFirstAndSkipDisabled() {
        RunParamsState state = new RunParamsState();
        state.add(RunParamsState.parse("--retries=2", false));
        state.add(RunParamsState.parse("--maxfail=0 -k \"smoke a\"", true));
        state.params().get(0).enabled = false;
        assertEquals("--maxfail=0 -k \"smoke a\"", state.arguments());
    }

    @Test
    void addingSameNameUpdatesInsteadOfDuplicating() {
        RunParamsState state = new RunParamsState();
        state.add(RunParamsState.parse("--debugging=0", false));
        state.params().get(0).enabled = false;
        state.add(RunParamsState.parse("--debugging=1", false));
        assertEquals(1, state.params().size());
        assertTrue(state.params().get(0).enabled);
        assertEquals("--debugging=1", state.arguments());
    }

    @Test
    void binaryOptionCheckboxIsTheValueAndIsAlwaysPassed() {
        RunParamsState state = new RunParamsState();
        state.add(RunParamsState.parse("--debugging=0", false));
        RunParamsState.Param debugging = state.params().get(0);
        debugging.binary = true;
        assertEquals("--debugging", debugging.label());
        assertFalse(debugging.on());
        assertEquals("--debugging=0", state.arguments());
        debugging.setOn(true);
        assertEquals("--debugging=1", state.arguments());
        assertTrue(debugging.included());
    }

    @Test
    void regularOptionCheckboxIncludesOrExcludes() {
        RunParamsState state = new RunParamsState();
        state.add(RunParamsState.parse("--maxfail=1", true));
        RunParamsState.Param maxfail = state.params().get(0);
        assertEquals("--maxfail=1", maxfail.label());
        maxfail.setOn(false);
        assertEquals("", state.arguments());
    }

    @Test
    void progressSwitchesModulesAndLatestResultWins() {
        RunParamsState state = new RunParamsState();
        state.trackModule("/p/tests/test_a.py");
        state.record("test_x", false);
        state.record("test_x", true);
        assertEquals(List.of("test_x"), state.getState().passed);
        assertTrue(state.getState().failed.isEmpty());
        state.trackModule("/p/tests/test_b.py");
        assertTrue(state.getState().passed.isEmpty());
    }
}
