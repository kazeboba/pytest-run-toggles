package dev.autotests.runtoggles;

/** Status of a test in the counted module. A click in the module list moves it to the next one:
 * passed → not run (joins the clean-up rerun) → failed → passed (leaves the clean-up rerun). */
enum TestStatus {
    PASSED, NOT_RUN, FAILED;

    TestStatus next() {
        return values()[(ordinal() + 1) % values().length];
    }

    static TestStatus of(RunParamsState.Data data, String test) {
        return data.failed.contains(test) ? FAILED : data.passed.contains(test) ? PASSED : NOT_RUN;
    }
}
