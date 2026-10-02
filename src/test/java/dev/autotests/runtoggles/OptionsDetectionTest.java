package dev.autotests.runtoggles;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OptionsDetectionTest {

    @Test
    void conftestIntOptionsWithDefaults(@TempDir Path dir) throws IOException {
        Path conftest = dir.resolve("conftest.py");
        Files.writeString(conftest, """
                def pytest_addoption(parser):
                    g = parser.getgroup("x")
                    g.addoption("--debugging", type=int, default=1,
                                help="1 = debug (no reporting)")
                    g.addoption("--retries", type=int, default=3, help="count")
                    g.addoption("--level", type=int, help="no default")
                    g.addoption("--stand", default="prod", help="string")
                """);
        assertEquals(Map.of("--debugging", "1", "--retries", "3", "--level", "0"), ConftestOptions.intDefaults(conftest));
    }

    @Test
    void missingConftestGivesNoOptions(@TempDir Path dir) {
        assertTrue(ConftestOptions.intDefaults(dir.resolve("conftest.py")).isEmpty());
    }

    @Test
    void markBinaryOnlyForZeroOneDefaults() {
        List<RunParamsState.Param> params = RunParamsState.parse("--debugging=0 --retries=2 --stand=dev", false);
        RunParamsSync.markBinary(params, Map.of("--debugging", "1", "--retries", "3"));
        assertTrue(params.get(0).binary);
        assertFalse(params.get(1).binary);
        assertFalse(params.get(2).binary);
    }

    @Test
    void disabledZeroOneOptionGetsItsDefaultSoPytestSeesTheSame() {
        List<RunParamsState.Param> params = RunParamsState.parse("--debugging=0", false);
        params.get(0).enabled = false;
        RunParamsSync.markBinary(params, Map.of("--debugging", "1"));
        assertTrue(params.get(0).binary);
        assertEquals("1", params.get(0).value);
        assertTrue(params.get(0).enabled);
    }

    @Test
    void nonZeroOneValueStaysNumeric() {
        List<RunParamsState.Param> params = RunParamsState.parse("--debugging=5", false);
        RunParamsSync.markBinary(params, Map.of("--debugging", "1"));
        assertFalse(params.get(0).binary);
    }

    @Test
    void standardCatalog() {
        assertTrue(StandardOption.isInt("--maxfail"));
        assertTrue(StandardOption.isStandard("-k"));
        assertFalse(StandardOption.isInt("-k"));
        assertFalse(StandardOption.isStandard("--debugging"));
    }
}
