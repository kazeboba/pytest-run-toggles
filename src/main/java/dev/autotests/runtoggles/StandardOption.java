package dev.autotests.runtoggles;

/** Standard pytest options for the «+» button; value — default value, null — a flag. */
enum StandardOption {
    EXIT_FIRST("-x", null, "stop on first failure"),
    MAX_FAIL("--maxfail", "0", "failures before stopping, 0 — never stop", true),
    VERBOSE("-v", null, "verbose output"),
    QUIET("-q", null, "quiet output"),
    LAST_FAILED("--lf", null, "rerun only the last failed tests"),
    FAILED_FIRST("--ff", null, "run the last failed tests first"),
    STEPWISE("--sw", null, "stepwise: stop on failure, resume from it next time"),
    KEYWORD("-k", "", "filter tests by name expression"),
    MARKER("-m", "", "filter tests by marker expression"),
    TRACEBACK("--tb", "short", "traceback format"),
    DURATIONS("--durations", "10", "show the slowest tests", true),
    PLUGIN("-p", "no:warnings", "disable a pytest plugin"),
    NO_CAPTURE("-s", null, "disable output capturing"),
    COLLECT_ONLY("--collect-only", null, "only collect tests");

    final String name;
    final String value;
    final String hint;
    final boolean integer;

    StandardOption(String name, String value, String hint) {
        this(name, value, hint, false);
    }

    StandardOption(String name, String value, String hint, boolean integer) {
        this.name = name;
        this.value = value;
        this.hint = hint;
        this.integer = integer;
    }

    String display() {
        return name + " — " + hint;
    }

    RunParamsState.Param toParam() {
        RunParamsState.Param param = new RunParamsState.Param(name, value, true);
        param.numeric = integer;
        return param;
    }

    static boolean isStandard(String name) {
        for (StandardOption option : values()) {
            if (option.name.equals(name)) {
                return true;
            }
        }
        return false;
    }

    static boolean isInt(String name) {
        for (StandardOption option : values()) {
            if (option.integer && option.name.equals(name)) {
                return true;
            }
        }
        return false;
    }
}
