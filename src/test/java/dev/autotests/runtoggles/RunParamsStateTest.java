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

    private static RunParamsState.Param titled(String name, String title, String value, boolean enabled) {
        RunParamsState.Param param = new RunParamsState.Param(name, value, false);
        param.title = title;
        param.enabled = enabled;
        return param;
    }

    @Test
    void titleGivesAnOptionItsCheckboxName() {
        List<RunParamsState.Param> params = RunParamsState.parseInput("Device: --serial=R58M123456A", false);
        assertEquals(1, params.size());
        assertEquals("Device", params.get(0).label());
        assertEquals("--serial=R58M123456A", String.join(" ", params.get(0).tokens()));
        assertEquals("Pixel 10", RunParamsState.parseInput("Pixel 10 : --serial=1A2B3C4D5E6F", false).get(0).title);
    }

    @Test
    void optionTextWithAColonIsNotATitle() {
        assertNull(RunParamsState.splitTitle("-p no:warnings"));
        assertNull(RunParamsState.splitTitle("--serial=R58M123456A"));
        assertNull(RunParamsState.parseInput("-p no:warnings", false).get(0).title);
    }

    @Test
    void titleWithSeveralOptionsIsNotApplied() {
        assertTrue(RunParamsState.splitTitle("Fast: -x --maxfail=1") != null);
        List<RunParamsState.Param> params = RunParamsState.parseInput("Fast: -x --maxfail=1", false);
        assertEquals(2, params.size());
        assertNull(params.get(0).title);
    }

    @Test
    void bracesMakeOneOptionWithAnAdjustableChoice() {
        List<RunParamsState.Param> params = RunParamsState.parseInput(
                "--serial={Samsung: R58M123456A, Pixel: 1A2B3C4D5E6F}", false);
        assertEquals(1, params.size());
        RunParamsState.Param serial = params.get(0);
        assertEquals("--serial", serial.name);
        assertNull(serial.title);
        assertEquals(2, serial.choices.size());
        assertEquals("Samsung", serial.choices.get(0).label);
        assertEquals("R58M123456A", serial.choices.get(0).value);
        assertEquals("--serial=R58M123456A", String.join(" ", serial.tokens()));
        serial.select(1);
        assertEquals("--serial=1A2B3C4D5E6F", String.join(" ", serial.tokens()));
        assertEquals("--serial=Pixel", serial.label());
    }

    @Test
    void titleOfAnOptionWithChoicesIsItsCheckboxName() {
        RunParamsState.Param device = RunParamsState.parseInput("Device: --serial={Samsung: A1, Pixel: B2}", false).get(0);
        assertEquals("Device", device.label());
        assertEquals("--serial", device.name);
        assertEquals(2, device.choices.size());
    }

    @Test
    void anEntryWithoutAColonIsAValueWithNoLabel() {
        List<RunParamsState.Choice> choices = RunParamsState.parseChoices("A1, Pixel: B2, : C3, ,");
        assertEquals(3, choices.size());
        assertEquals("A1", choices.get(0).label);
        assertEquals("A1", choices.get(0).value);
        assertEquals("C3", choices.get(2).label);
        assertEquals("A1", choices.get(0).display());
        assertEquals("Pixel: B2", choices.get(1).display());
    }

    @Test
    void emptyBracesAreNotChoices() {
        List<RunParamsState.Param> params = RunParamsState.parseInput("--serial={}", false);
        assertEquals(1, params.size());
        assertTrue(params.get(0).choices.isEmpty());
    }

    @Test
    void selectedChoiceIsPassedOnlyWhileTheOptionIsOn() {
        RunParamsState state = new RunParamsState();
        state.add(RunParamsState.parseInput("--serial={Samsung: A1, Pixel: B2}", false));
        RunParamsState.Param serial = state.params().get(0);
        assertEquals("--serial=A1", state.arguments());
        serial.select(1);
        assertEquals("--serial=B2", state.arguments());
        serial.setOn(false);
        assertEquals("", state.arguments());
    }

    @Test
    void addingAnOptionWithChoicesAgainReplacesItsChoices() {
        RunParamsState state = new RunParamsState();
        state.add(RunParamsState.parseInput("--serial={Samsung: A1, Pixel: B2}", false));
        state.params().get(0).select(1);
        state.add(RunParamsState.parseInput("--serial={Tablet: C3}", false));
        assertEquals(1, state.params().size());
        assertEquals(1, state.params().get(0).choices.size());
        assertEquals("--serial=C3", state.arguments());
    }

    @Test
    void oldPresetsOfOneOptionBecomeOneRowWithADropdown() {
        RunParamsState state = new RunParamsState();
        state.params().add(titled("--serial", "Samsung", "R58M123456A", false));
        state.params().add(titled("--serial", "Pixel", "1A2B3C4D5E6F", true));
        state.params().add(titled("--maxfail", "Stop", "1", true));
        state.migratePresets();
        assertEquals(2, state.params().size());
        RunParamsState.Param serial = state.params().get(0);
        assertEquals(2, serial.choices.size());
        assertEquals("Samsung", serial.choices.get(0).label);
        assertEquals(1, serial.selected);
        assertTrue(serial.enabled);
        assertNull(serial.title);
        assertEquals("--serial=1A2B3C4D5E6F --maxfail=1", state.arguments());
        // a title of a single row stays a title
        assertEquals("Stop", state.params().get(1).title);
    }

    @Test
    void presetsThatWereAllOffStayOffAfterTheMigration() {
        RunParamsState state = new RunParamsState();
        state.params().add(titled("--serial", "Samsung", "A1", false));
        state.params().add(titled("--serial", "Pixel", "B2", false));
        state.migratePresets();
        assertEquals(1, state.params().size());
        assertFalse(state.params().get(0).enabled);
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

    @Test
    void handStatusPassedLeavesTheRerunAndFailedJoinsIt() {
        RunParamsState state = new RunParamsState();
        state.record("test_a", false);
        state.setStatus("test_a", TestStatus.PASSED);
        assertEquals(List.of("test_a"), state.getState().passed);
        assertTrue(state.getState().failed.isEmpty());
        state.setStatus("test_a", TestStatus.FAILED);
        assertEquals(List.of("test_a"), state.getState().failed);
        assertTrue(state.getState().passed.isEmpty());
    }

    @Test
    void handStatusNotRunIsKeptForParametrizedIds() {
        RunParamsState state = new RunParamsState();
        state.record("test_p[1]", true);
        state.record("test_p[2]", true);
        state.setStatus("test_p[1]", TestStatus.NOT_RUN);
        assertEquals(List.of("test_p[2]"), state.getState().passed);
        assertEquals(List.of("test_p[1]"), state.getState().pending);
        assertEquals(TestStatus.NOT_RUN, TestStatus.of(state.getState(), "test_p[1]"));
    }

    @Test
    void realResultReplacesHandStatusOfTheTestAndOfItsBase() {
        RunParamsState state = new RunParamsState();
        state.setStatus("test_p", TestStatus.FAILED);
        state.setStatus("test_q[1]", TestStatus.NOT_RUN);
        state.record("test_p[1]", true);
        state.record("test_q[1]", false);
        assertTrue(state.getState().pending.isEmpty());
        assertEquals(List.of("test_p[1]"), state.getState().passed);
        assertEquals(List.of("test_q[1]"), state.getState().failed);
    }

    @Test
    void resetForgetsHandStatuses() {
        RunParamsState state = new RunParamsState();
        state.setStatus("test_a", TestStatus.NOT_RUN);
        state.resetProgress();
        assertTrue(state.getState().pending.isEmpty());
    }
}
