# Pytest Run Params

PyCharm plugin: pytest run options as checkboxes.

- **Pytest Params** tool window (right side):
  - **Standard** — pytest options, added with «+» from a list (`-x`, `--maxfail`, `-k`, `--lf`…);
  - **Custom** — input field (`--debugging=0`, `--retest=1`, several at once separated by spaces, Enter adds).
- **Run tab toolbar** of pytest runs: one checkbox per option (single click, then Rerun); the gear opens the panel.
- **Status bar**: the included options in one line; click opens a checkbox menu with «Edit…».

**Module progress** (Run tab toolbar: `✔ passed  ✖ failed  ○ not run`):
- test results of the module (a test file) are counted from PyCharm's test runner events; the latest result
  of a test wins;
- a module run of another file switches counting to it and starts from scratch; folder runs are ignored,
  single tests of the counted module update its results; skipped tests stay «not run»;
- clicking the counters opens a menu: **Clean up module**, **Reset progress** and the tests (choosing one opens it).

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

Unit tests (pure logic: parsing, arguments, conftest detection, test keys, `--deselect`): `./gradlew test`.

## Install
Unzip into `~/Library/Application Support/JetBrains/PyCharmCE2023.1/plugins/` (remove the old jar from
`at-run-toggles/lib`) or Settings → Plugins → ⚙ → Install Plugin from Disk, then restart PyCharm.
After a major PyCharm update, fix the path in `build.gradle.kts` and rebuild.
