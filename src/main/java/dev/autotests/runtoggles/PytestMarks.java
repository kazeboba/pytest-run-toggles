package dev.autotests.runtoggles;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tags of the tests of a module: the {@code @pytest.mark.<name>} decorators, read from the text of the file (not from
 * the PSI, so no IDE API is involved). The marks of pytest itself (parametrize, skip, …) are not tags. */
final class PytestMarks {

    private static final Set<String> BUILTIN = Set.of("parametrize", "skip", "skipif", "xfail", "usefixtures", "filterwarnings");
    private static final Pattern MARK = Pattern.compile("(?:pytest\\.)?mark\\.([A-Za-z_]\\w*)");
    private static final Pattern CLASS = Pattern.compile("^class\\s+(Test\\w*)");
    private static final Pattern TEST = Pattern.compile("^(?:async\\s+)?def\\s+(test\\w*)");
    private static final Pattern MODULE_MARK = Pattern.compile("^pytestmark\\s*=\\s*(.*)$");

    private PytestMarks() {
    }

    /** Test key ('test_x', 'TestClass::test_y') → its tags: its own decorators, those of its class and the module's
     * {@code pytestmark}. */
    static Map<String, Set<String>> scan(String text) {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        String[] lines = text.split("\n", -1);
        Set<String> moduleTags = moduleTags(lines);
        Set<String> pending = new LinkedHashSet<>();
        String currentClass = null;
        Set<String> classTags = Set.of();
        for (String line : lines) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int indent = line.length() - line.stripLeading().length();
            if (trimmed.startsWith("@")) {
                Matcher mark = MARK.matcher(trimmed);
                if (mark.find() && !BUILTIN.contains(mark.group(1))) {
                    pending.add(mark.group(1));
                }
                continue;
            }
            Matcher cls = CLASS.matcher(trimmed);
            if (indent == 0 && cls.find()) {
                currentClass = cls.group(1);
                classTags = union(moduleTags, pending);
                pending.clear();
                continue;
            }
            Matcher test = TEST.matcher(trimmed);
            if (test.find()) {
                if (indent == 0) {
                    currentClass = null;
                    result.put(test.group(1), union(moduleTags, pending));
                } else if (currentClass != null) {
                    result.put(currentClass + "::" + test.group(1), union(classTags, pending));
                }
                pending.clear();
                continue;
            }
            // a statement at the top level ends the class (the closing bracket of a multi-line decorator does not)
            if (indent == 0 && Character.isLetter(trimmed.charAt(0))) {
                currentClass = null;
                pending.clear();
            }
        }
        return result;
    }

    /** The tags of a module's tests, all of them, sorted, with the number of tests that have each one. */
    static Map<String, Integer> counts(Map<String, Set<String>> tests) {
        Map<String, Integer> counts = new TreeMap<>();
        tests.values().forEach(tags -> tags.forEach(tag -> counts.merge(tag, 1, Integer::sum)));
        return counts;
    }

    /** {@code pytestmark = pytest.mark.a} or {@code pytestmark = [pytest.mark.a, pytest.mark.b]} (the list may go on
     * over the next lines, up to the closing bracket). */
    private static Set<String> moduleTags(String[] lines) {
        Set<String> tags = new LinkedHashSet<>();
        for (int i = 0; i < lines.length; i++) {
            Matcher module = MODULE_MARK.matcher(lines[i].strip());
            if (!module.matches()) {
                continue;
            }
            StringBuilder value = new StringBuilder(module.group(1));
            int open = count(value, '[') + count(value, '(') - count(value, ']') - count(value, ')');
            for (int j = i + 1; open > 0 && j < lines.length; j++) {
                value.append(' ').append(lines[j].strip());
                open = count(value, '[') + count(value, '(') - count(value, ']') - count(value, ')');
            }
            Matcher mark = MARK.matcher(value);
            while (mark.find()) {
                if (!BUILTIN.contains(mark.group(1))) {
                    tags.add(mark.group(1));
                }
            }
        }
        return tags;
    }

    private static int count(CharSequence text, char c) {
        int n = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == c) {
                n++;
            }
        }
        return n;
    }

    private static Set<String> union(Set<String> a, Set<String> b) {
        Set<String> out = new LinkedHashSet<>(a);
        out.addAll(b);
        return out;
    }
}
