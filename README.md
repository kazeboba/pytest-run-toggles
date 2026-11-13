# Pytest Run Params

PyCharm plugin: pytest run options as checkboxes.

Requires IDE 2024.3 or newer (build 243+) with the Python plugin: PyCharm, or another JetBrains IDE where the
Python plugin is installed.

- The **PytestParams** tab is always in the Run panel and is its first tab (the tabs of the real runs come after
  it); it has no process and cannot be closed. It is made when the project opens. The **Pytest Params** button on
  the left sidebar opens the Run panel on this tab — so it can be opened right after the IDE starts, before any run.
  The IDE's own **Run** button opens the standard Run panel: if the PytestParams tab is the selected one, the
  latest real run is brought to the front. The gear of the Run toolbar and «Edit…» of the status bar menu open the
  tab too. The plugin adds nothing to the Project view menu except **Add to Sequence** (see below).
- The tab has a **Run** button at the top, in the middle; **Clean Up Module**, the progress counters and the reset button
  (see below) are only on the toolbar of the Run tabs — and two columns:
  - **Left — the options**: Standard first, Custom after, one column; every row is a drag handle (drag to move it),
    a checkbox, a value and a remove button. The **+** at the right of the «Params» title opens a window: a standard
    pytest option from a list (`-x`, `--maxfail`, `-k`, `--lf`…) or a custom one typed in:
    - `--debugging=0`, `--retest=1` — several at once, separated by spaces;
    - `Title: --param=value` — the checkbox is named `Title`;
    - `[Title:] --serial={Samsung: R58M123456A, Pixel: 1A2B3C4D5E6F}` — **one option with a dropdown of values**: a
      checkbox (named `Title`, or `--serial`) and a choice between `Samsung: R58M123456A` and `Pixel: 1A2B3C4D5E6F`;
      the selected value is what the option passes. (On the Run toolbar and in the status bar menu the option is a
      checkbox `serial=Samsung`; the value is chosen on the tab.) The separate `Samsung` and `Pixel` rows of the
      earlier build become one such row.
  - **Right — what to run**: a tree of directories, modules and tests of the project with checkboxes (ticking a
    directory ticks what is in it), a **search** (by test id or name; several, separated by comma or space — paste
    `8211108, 8220829` to see them) and **Clear** (on the line of «Tests to run», at its right: unticks everything,
    the search stays). (Selecting by `@pytest.mark.<name>` marks is out for now.) A directory or
    a module is ticked when everything in it is, and is a minus when only some of it is; a click on a tick unticks all
    of it, a click on a minus ticks all of it. A **double click** on a module or a test opens it in the editor (a directory expands).
    Next to the id of a test, in grey, is the name of the case: the first line of the docstring under its `def`
    (cut with «…» where the row ends); the search looks in the names too. The scrolled areas of both columns have the same thin outline.
    The ticks and the search are kept in the project, so they are there when the tab is
    opened again. The directories and modules that have ticked tests are shown first: a tick moves
    them to the top 0.2 s after the last tick, the tests of a module keep the order of the code (the rows that were open
    stay open; the tree does not follow the row that moved: the scroll position and the selection stay as they were).
  - **Add to Sequence** (with the icon of the plugin) puts something into the ticked tests without opening the tab:
    in the editor, a right click on the **name of a test** (`def test_…`, also a method of a `Test…` class); in the
    Project view, a right click on **test modules and directories**. It is ticked in the tree, selected there and
    scrolled to; a hint in the editor says what was added. **Run** runs what is ticked: a single directory or module as itself (its
    progress is counted and Clean Up Module works for it), several of them or single tests as one run of the
    selection (a temporary configuration with the target «custom»: the options and the paths / node ids as
    arguments; not counted); with nothing ticked — the counted module or folder, else the `tests` folder. The run is
    shown in its own tab; any other target runs with the same options from the IDE's own Run (the options reach the
    pytest template and the temporary configurations).
- **Run tab toolbar** of pytest runs: one checkbox per option (single click, then Rerun); the gear opens the panel.
- **Status bar**: the included options in one line; click opens a checkbox menu with «Edit…».
- The **Run** button of the tab runs through a temporary configuration, so the options always reach it. The standard
  Run first looks for an existing configuration with the same target and reuses it — a saved folder preset
  (e.g. target `tests`) is run with its own arguments, and the options of the tab don't reach it.
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
- the **↺ Reset Progress** button next to the counters forgets the results (the next run runs all tests);
- clicking the counters opens the tests of the module or folder
  with a mark (green ✔ passed, grey ○ not run, red ✖ failed). A click on a test moves it to the next status
  (✔ → ○ → ✖ → ✔): a passed test set to ○ or ✖ joins the clean-up rerun, a failed one set to ✔ leaves it. The list stays open,
  so several tests can be switched in a row; the counters follow at once. ⌘/Ctrl-click opens the test.
  A status set by hand lasts until the test really runs (its result replaces it) or the progress is reset.
- **▶🧹 Clean Up Module** button (left of the counters, icon: play with a broom): runs the counted module or folder
  as a temporary configuration with the panel options and
  `--deselect <node id>` for every passed test — only failed and not run tests run, so after fixes the same run can be
  repeated until nothing is left. The `--deselect` is for that one run only: Rerun and ▶ run everything again. Disabled when
  every test has passed; shown with the counters («Show module progress» in Settings).
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
First copy `local.properties.example` to `local.properties` (not committed) and set `ide.path` — the installed IDE
(the `.app` folder on macOS) the plugin is built against and verified with. Then:
```bash
JAVA_HOME="<ide.path>/Contents/jbr/Contents/Home" ./gradlew buildPlugin
```
Output — `build/distributions/at-run-toggles-<version>.zip`.

Needs Gradle 8.14+ and IntelliJ Platform Gradle Plugin 2.10+ for PyCharm 2025.2 (older ones can't resolve its module jars);
JUnit 4 is on the test runtime classpath because the IDE's test framework needs it.

Unit tests (pure logic: parsing, arguments, conftest detection, test keys, `--deselect`): `./gradlew test`.

## Install
Unzip into `~/Library/Application Support/JetBrains/PyCharm<version>/plugins/` (remove the old jar from
`at-run-toggles/lib`) or Settings → Plugins → ⚙ → Install Plugin from Disk, then restart PyCharm.
After a major PyCharm update, fix `ide.path` in `local.properties` and rebuild.
