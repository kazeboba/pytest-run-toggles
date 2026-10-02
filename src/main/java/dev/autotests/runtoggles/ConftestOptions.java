package dev.autotests.runtoggles;

import com.intellij.openapi.project.Project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Project int options from pytest_addoption in conftest.py: name → default (no default — "0"). */
final class ConftestOptions {

    private static final Pattern OPTION = Pattern.compile("addoption\\(\\s*[\"'](--[\\w-]+)[\"']([^)]*)");
    private static final Pattern INT_TYPE = Pattern.compile("type\\s*=\\s*int\\b");
    private static final Pattern DEFAULT = Pattern.compile("default\\s*=\\s*(-?\\d+)");

    private ConftestOptions() {
    }

    static Map<String, String> intDefaults(Project project) {
        return project.getBasePath() == null ? Map.of() : intDefaults(Path.of(project.getBasePath(), "conftest.py"));
    }

    static Map<String, String> intDefaults(Path conftest) {
        Map<String, String> options = new HashMap<>();
        try {
            Matcher option = OPTION.matcher(Files.readString(conftest));
            while (option.find()) {
                if (INT_TYPE.matcher(option.group(2)).find()) {
                    Matcher value = DEFAULT.matcher(option.group(2));
                    options.put(option.group(1), value.find() ? value.group(1) : "0");
                }
            }
        } catch (IOException ignored) {
            // no conftest.py — numeric options are detected by the catalog and by value
        }
        return options;
    }
}
