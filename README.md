# Pytest Run Params

PyCharm plugin: pytest run options as checkboxes.

Requires IDE 2024.3 or newer (build 243+) with the Python plugin: PyCharm, or another JetBrains IDE where the
Python plugin is installed.

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
- test results of the module (a test file) or of a folder (all test modules below it, its tests are listed as
  `sub/test_mod.py::test_x`) are counted from PyCharm's test runner events; the latest result of a test wins;
- a run of another module or folder switches counting to it and starts from scratch; a module, a subfolder or
  a single test inside the counted folder updates its results instead; single tests of the counted module update
  its results; skipped tests stay «not run»;
- clicking the counters opens a menu: **Reset progress** (forgets the results) and the tests of the module or folder
  with a mark (green ✔ passed, grey ○ not run, red ✖ failed). A click on a test moves it to the next status
  (✔ → ○ → ✖ → ✔): a passed test set to ○ or ✖ joins the clean-up rerun, a failed one set to ✔ leaves it. The list stays open,
  so several tests can be switched in a row; the counters follow at once. ⌘/Ctrl-click opens the test.
  A status set by hand lasts until the test really runs (its result replaces it) or the progress is reset.
- **▶🧹 Clean Up Module** button (left of the counters, icon: play with a broom; also in the Project view menu of a test
  module or folder): runs the counted module or folder as a temporary configuration with the panel options and
  `--deselect <node id>` for every passed test — only failed and not run tests run, so after fixes the same run can be
  repeated until nothing is left. The `--deselect` is for that one run only: Rerun and ▶ run everything again. Disabled when
  every test has passed; shown with the counters («Show module progress» in Settings). In the Project view menu
  the broom on another module or folder switches the counter to it (progress from scratch) and runs it.
- A passed test whose id starts an unfinished test's id is kept in the rerun (pytest matches `--deselect` by prefix).
  Runs of other modules or folders (and of a module inside the counted folder) are not filtered.

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
Unzip into `~/Library/Application Support/JetBrains/PyCharm<version>/plugins/` (remove the old jar from
`at-run-toggles/lib`) or Settings → Plugins → ⚙ → Install Plugin from Disk, then restart PyCharm.
After a major PyCharm update, fix the path in `build.gradle.kts` and rebuild.
