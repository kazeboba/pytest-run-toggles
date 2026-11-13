package dev.autotests.runtoggles;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PytestParamsTabTest {

    @Test
    void theStandardPanelIsTheLatestTabOfARealRun() {
        assertEquals(2, PytestParamsTab.lastRealIndex(List.of(true, false, false, true)));
        assertEquals(1, PytestParamsTab.lastRealIndex(List.of(true, false)));
    }

    @Test
    void withoutARealRunThereIsNoStandardTab() {
        assertEquals(-1, PytestParamsTab.lastRealIndex(List.of(true)));
        assertEquals(-1, PytestParamsTab.lastRealIndex(List.of()));
    }
}
