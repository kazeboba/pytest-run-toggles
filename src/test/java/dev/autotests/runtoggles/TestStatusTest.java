package dev.autotests.runtoggles;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestStatusTest {

    @Test
    void clickCyclesPassedNotRunFailedPassed() {
        assertEquals(TestStatus.NOT_RUN, TestStatus.PASSED.next());
        assertEquals(TestStatus.FAILED, TestStatus.NOT_RUN.next());
        assertEquals(TestStatus.PASSED, TestStatus.FAILED.next());
    }

    @Test
    void statusIsReadFromTheResults() {
        RunParamsState.Data data = new RunParamsState.Data();
        data.passed.add("test_a");
        data.failed.add("test_b");
        assertEquals(TestStatus.PASSED, TestStatus.of(data, "test_a"));
        assertEquals(TestStatus.FAILED, TestStatus.of(data, "test_b"));
        assertEquals(TestStatus.NOT_RUN, TestStatus.of(data, "test_c"));
    }
}
