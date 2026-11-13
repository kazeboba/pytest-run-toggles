package dev.autotests.runtoggles;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PytestMarksTest {

    @Test
    void decoratorsOfAFunctionAreItsTags() {
        Map<String, Set<String>> tags = PytestMarks.scan("""
                import pytest

                @pytest.mark.vip
                @pytest.mark.smoke
                def test_case_1(action):
                    pass

                def test_case_2(action):
                    pass
                """);
        assertEquals(Set.of("vip", "smoke"), tags.get("test_case_1"));
        assertEquals(Set.of(), tags.get("test_case_2"));
    }

    @Test
    void marksOfPytestItselfAreNotTags() {
        Map<String, Set<String>> tags = PytestMarks.scan("""
                @pytest.mark.parametrize("user", ["a", "b"], ids=["a", "b"])
                @pytest.mark.skip(reason="x")
                @pytest.mark.regress
                def test_case_3(user):
                    pass
                """);
        assertEquals(Set.of("regress"), tags.get("test_case_3"));
    }

    @Test
    void aMultiLineDecoratorKeepsTheTagsAboveIt() {
        Map<String, Set<String>> tags = PytestMarks.scan("""
                @pytest.mark.vip
                @pytest.mark.parametrize(
                    "user",
                    [1, 2],
                )
                def test_case_4(user):
                    pass
                """);
        assertEquals(Set.of("vip"), tags.get("test_case_4"));
    }

    @Test
    void classAndModuleTagsReachTheTestsInside() {
        Map<String, Set<String>> tags = PytestMarks.scan("""
                pytestmark = [pytest.mark.slow,
                              pytest.mark.device]

                @pytest.mark.vip
                class TestGroup:
                    @pytest.mark.smoke
                    def test_a(self):
                        pass

                    def test_b(self):
                        pass

                def test_top():
                    pass
                """);
        assertEquals(Set.of("slow", "device", "vip", "smoke"), tags.get("TestGroup::test_a"));
        assertEquals(Set.of("slow", "device", "vip"), tags.get("TestGroup::test_b"));
        assertEquals(Set.of("slow", "device"), tags.get("test_top"));
    }

    @Test
    void aDecoratorOfAHelperDoesNotLeakIntoTheNextTest() {
        Map<String, Set<String>> tags = PytestMarks.scan("""
                @pytest.mark.vip
                def helper():
                    pass

                def test_after():
                    pass
                """);
        assertEquals(Set.of(), tags.get("test_after"));
        assertTrue(!tags.containsKey("helper"));
    }

    @Test
    void countsListEveryTagOnceWithTheNumberOfItsTests() {
        Map<String, Integer> counts = PytestMarks.counts(Map.of(
                "test_a", Set.of("vip", "smoke"), "test_b", Set.of("vip"), "test_c", Set.of()));
        assertEquals(Map.of("smoke", 1, "vip", 2), counts);
    }
}
