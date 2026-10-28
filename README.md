# Pytest Run Params

PyCharm plugin: pytest run options as checkboxes.

- **Pytest Params** tool window (right side):
  - **Standard** — pytest options, added with «+» from a list (`-x`, `--maxfail`, `-k`, `--lf`…);
  - **Custom** — input field (`--debugging=0`, `--retest=1`, several at once separated by spaces, Enter adds).
- **Run tab toolbar** of pytest runs: one checkbox per option (single click, then Rerun); the gear opens the panel.
- **Status bar**: the included options in one line; click opens a checkbox menu with «Edit…».
- **Run with Pytest Params** (Project view context menu of a folder or a test file): runs it as a temporary
  configuration with the panel options. The standard Run first looks for an existing configuration with the same
  target and reuses it — a saved folder preset (e.g. target `tests`) is run with its own arguments, and the panel
  doesn't reach it.
- **Run tab of a saved configuration** (e.g. a folder preset): the checkboxes show and change that configuration's own
  Additional Arguments — a 0/1 option gets an explicit value, another option is removed or added with the panel value;
  Rerun applies them. The panel and other configurations are not affected.
- **Autodetect** test configurations (the default test runner of a new project) are handled like pytest ones.

**Module progress** (Run tab toolbar: `✔ passed  ✖ failed  ○ not run`):
- test results of the module (a test file) are counted from PyCharm's test runner events; the latest result
  of a test wins;
- a module run of another file switches counting to it and starts from scratch; folder runs are ignored,
  single tests of the counted module update its results; skipped tests stay «not run»;
- clicking the counters opens a menu: **Clean up module**, **Reset progress** and the tests of the module with a mark
  (green ✔ passed, grey ○ not run, red ✖ failed). A click on a test moves it to the next status
  (✔ → ○ → ✖ → ✔): a passed test set to ○ or ✖ joins the clean-up rerun, a failed one set to ✔ leaves it. The list stays open,
  so several tests can be switched in a row; the counters and the `--deselect` follow at once. ⌘/Ctrl-click opens the test.
  A status set by hand lasts until the test really runs (its result replaces it) or the progress is reset.
- **▶🧹 Clean Up Module** button (left of the counters, icon: play with a broom): runs the counted module as a temporary
  configuration with the panel options and `--deselect` for every passed test — only failed and not run tests run,
  exactly as with **Clean up module** ticked. The tick itself stays as it was (the `--deselect` is not left in the
  configuration, Rerun and ▶ run the full module again unless the tick is on). Disabled when every test has passed;
  shown with the counters («Show module progress» in Settings).

**Clean up module** (off by default, for projects without their own skipping of passed tests — pytest's `--lf`
remembers only the last failures): while it is on, a run of the counted module gets `--deselect <module>::<test>`
for every passed test, so after fixes the module is run again and again until no failed or not run tests remain.
A passed test whose id starts an unfinished test's id is kept (pytest matches `--deselect` by prefix).
Folder runs and other modules are not filtered. «Reset progress» forgets the results.

Row = drag handle ⋮⋮ (reorder within the block; order = argument order) + checkbox + value + remove.
- **0/1 options** (int options with default 0 or 1 in the project's `conftest.py`, e.g. `--debugging`):
  the checkbox is the value — checked → `=1`, unchecked → `=0`, the argument is always passed.
- **Other options**: the checkbox includes/excludes the option; int values (conftest int options,
  `--maxfail`, `--durations`) get a number field.

Included rows become the **Additional Arguments** of the pytest template and of all temporary (▶) configurations —
entirely, manual edits there are overwritten. Saved configurations are not changed. On the first run the panel is
filled from the current pytest template arguments. Options are stored per project in `.idea/workspace.xml`.

## Settings
Settings → Tools → Pytest Run Params (per project): write options to the pytest template and/or temporary (▶)
configurations; show option checkboxes and/or module progress on the Run tab toolbar. The status bar widget is
toggled from the status bar context menu.

## Build
```bash
JAVA_HOME="/Applications/PyCharm CE.app/Contents/jbr/Contents/Home" ./gradlew buildPlugin
```
Built against the installed PyCharm (`build.gradle.kts`, `local(...)`); output —
`build/distributions/at-run-toggles-<version>.zip`.

Needs Gradle 8.14+ and IntelliJ Platform Gradle Plugin 2.10+ for PyCharm 2025.2 (older ones can't resolve its module jars);
JUnit 4 is on the test runtime classpath because the IDE's test framework needs it.

Unit tests (pure logic: parsing, arguments, conftest detection, test keys, `--deselect`): `./gradlew test`.

## Install
Unzip into `~/Library/Application Support/JetBrains/PyCharmCE2023.1/plugins/` (remove the old jar from
`at-run-toggles/lib`) or Settings → Plugins → ⚙ → Install Plugin from Disk, then restart PyCharm.
After a major PyCharm update, fix the path in `build.gradle.kts` and rebuild.
