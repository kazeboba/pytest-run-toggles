package dev.autotests.runtoggles;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigArgsTest {

    private static RunParamsState.Param binary(String name, String conftestDefault) {
        RunParamsState.Param param = new RunParamsState.Param(name, "1", false);
        param.binary = true;
        param.conftestDefault = conftestDefault;
        return param;
    }

    @Test
    void binaryOptionIsSwitchedInPlace() {
        RunParamsState.Param debugging = binary("--debugging", "0");
        assertTrue(ConfigArgs.isOn("--maxfail=0 --debugging=1 -x", debugging));
        assertEquals("--maxfail=0 --debugging=0 -x", ConfigArgs.toggle("--maxfail=0 --debugging=1 -x", debugging, false));
    }

    @Test
    void absentBinaryOptionFollowsConftestDefaultAndIsAddedExplicitly() {
        assertFalse(ConfigArgs.isOn("--maxfail=0", binary("--retest", "0")));
        assertTrue(ConfigArgs.isOn("--maxfail=0", binary("--skip-failed", "1")));
        assertEquals("--maxfail=0 --retest=1", ConfigArgs.toggle("--maxfail=0", binary("--retest", "0"), true));
    }

    @Test
    void presentOptionKeepsItsOwnValue() {
        RunParamsState.Param maxfail = new RunParamsState.Param("--maxfail", "0", true);
        String args = "--maxfail=1 --debugging=0";
        assertTrue(ConfigArgs.isOn(args, maxfail));
        assertEquals("maxfail=1", ConfigArgs.label(args, maxfail));
        assertEquals(args, ConfigArgs.toggle(args, maxfail, true));
        assertEquals("--debugging=0", ConfigArgs.toggle(args, maxfail, false));
    }

    @Test
    void absentOptionIsAddedWithThePanelValue() {
        RunParamsState.Param maxfail = new RunParamsState.Param("--maxfail", "0", true);
        assertFalse(ConfigArgs.isOn("--debugging=1", maxfail));
        assertNull(ConfigArgs.label("--debugging=1", maxfail));
        assertEquals("--debugging=1 --maxfail=0", ConfigArgs.toggle("--debugging=1", maxfail, true));
    }

    @Test
    void separateAndQuotedValuesGoWithTheirOption() {
        RunParamsState.Param keyword = new RunParamsState.Param("-k", "smoke", true);
        assertEquals("-k smoke", ConfigArgs.label("-k smoke --debugging=1", keyword));
        assertEquals("--debugging=1", ConfigArgs.toggle("-k smoke --debugging=1", keyword, false));
        assertEquals("", ConfigArgs.toggle("-k \"smoke and fast\"", keyword, false));
    }

    @Test
    void optionWithChoicesIsPassedWithTheSelectedValue() {
        RunParamsState.Param serial = RunParamsState.parseInput("--serial={Samsung: A1, Pixel: B2}", false).get(0);
        serial.select(1);
        assertEquals("--maxfail=0 --serial=B2", ConfigArgs.toggle("--maxfail=0", serial, true));
        assertEquals("--maxfail=0", ConfigArgs.toggle("--maxfail=0 --serial=B2", serial, false));
        assertTrue(ConfigArgs.isOn("--serial=A1", serial));
    }

    @Test
    void optionWithATitleKeepsItInTheToolbar() {
        RunParamsState.Param device = RunParamsState.parseInput("Device: --serial={Samsung: A1}", false).get(0);
        assertEquals("Device", ConfigArgs.label("--serial=A1", device));
    }

    @Test
    void positionalArgumentsStay() {
        RunParamsState.Param exitFirst = new RunParamsState.Param("-x", null, true);
        assertEquals("tests", ConfigArgs.toggle("-x tests", exitFirst, false));
        assertEquals("tests", ConfigArgs.toggle("tests -x", exitFirst, false));
    }
}
