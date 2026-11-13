package dev.autotests.runtoggles;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import com.intellij.util.execution.ParametersListUtil;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Panel rows (stored in .idea/workspace.xml): a pytest option, on/off, standard/custom block. */
@Service(Service.Level.PROJECT)
@State(name = "AtRunParams", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public final class RunParamsState implements PersistentStateComponent<RunParamsState.Data> {

    /** One value of an option with choices: the label in the dropdown and what the option passes. */
    public static final class Choice {
        public String label = "";
        public String value = "";

        public Choice() {
        }

        Choice(String label, String value) {
            this.label = label;
            this.value = value;
        }

        /** 'Samsung: R58M123456A' (just the value when it has no label of its own). */
        String display() {
            return label.equals(value) ? value : label + ": " + value;
        }
    }

    public static final class Param {
        public String name = "";
        /** null — a flag without a value */
        public String value;
        public boolean enabled = true;
        public boolean standard;
        /** the value is an integer (number field); known int options are detected without this flag */
        public boolean numeric;
        /** 0/1 option (int with default 0/1 in conftest): the checkbox is the value, the argument is always passed */
        public boolean binary;
        /** conftest default of a 0/1 option: its state in a saved configuration that doesn't pass it */
        @Transient
        public String conftestDefault;
        /** The name the checkbox shows instead of the option ('Device' for '--serial'); null — the option itself. */
        public String title;
        /** Values to choose from ('Samsung' → 'R58M123456A'): the option shows a dropdown and passes the selected one.
         * Empty — an ordinary option. */
        public List<Choice> choices = new ArrayList<>();
        /** index of the selected choice */
        public int selected;

        public Param() {
        }

        Param(String name, String value, boolean standard) {
            this.name = name;
            this.value = value;
            this.standard = standard;
        }

        List<String> tokens() {
            if (binary) {
                return List.of(name + "=" + ("1".equals(value) ? "1" : "0"));
            }
            if (value == null) {
                return List.of(name);
            }
            return name.startsWith("--") ? List.of(name + "=" + value) : List.of(name, value);
        }

        /** Checkbox: for a 0/1 option — value 1, for others — the option is enabled. */
        boolean on() {
            return binary ? "1".equals(value) : enabled;
        }

        void setOn(boolean on) {
            if (binary) {
                value = on ? "1" : "0";
            } else {
                enabled = on;
            }
        }

        /** Included in the arguments: a 0/1 option always, others when enabled. */
        boolean included() {
            return binary || enabled;
        }

        /** Checkbox label: the title if there is one, a 0/1 option without its value ('--debugging'), an option with
         * choices as 'name=label' ('--serial=Samsung'), others as in the arguments. */
        String label() {
            if (hasTitle()) {
                return title;
            }
            if (binary) {
                return name;
            }
            return hasChoices() ? name + "=" + choices.get(clamped()).label : String.join(" ", tokens());
        }

        boolean hasTitle() {
            return title != null && !title.isBlank();
        }

        boolean hasChoices() {
            return !choices.isEmpty();
        }

        /** Picks a choice: its value is what the option passes from now on. */
        void select(int index) {
            if (index >= 0 && index < choices.size()) {
                selected = index;
                value = choices.get(index).value;
            }
        }

        private int clamped() {
            return Math.max(0, Math.min(selected, choices.size() - 1));
        }
    }

    public static final class Data {
        public List<Param> params = new ArrayList<>();
        public boolean seeded;
        /** absolute path of the test module or folder whose progress is counted (the field name stays: saved state) */
        public String progressModule;
        /** results in the tracked scope: 'test_x', 'TestClass::test_y', 'test_z[1]'; for a folder with the module
         * path in front: 'sub/test_mod.py::test_x' */
        public List<String> passed = new ArrayList<>();
        public List<String> failed = new ArrayList<>();
        /** tests set to 'not run' by hand: 'test_z[1]' can't be derived from the module's functions, so it is kept here */
        public List<String> pending = new ArrayList<>();
        /** what the Run button of the PytestParams tab runs: the directories, modules and tests that are ticked,
         * as the shortest list that covers them (project-relative: 'tests', 'tests/test_a.py', 'tests/test_a.py::test_x') */
        public List<String> selected = new ArrayList<>();
        /** what is typed in the search of the selection panel */
        public String selectionSearch = "";
        /** Settings → Tools → Pytest Run Params */
        public boolean manageTemplate = true;
        public boolean manageTemporary = true;
        public boolean toolbarOptions = true;
        public boolean toolbarProgress = true;
    }

    private Data data = new Data();

    public static RunParamsState getInstance(@NotNull Project project) {
        return project.getService(RunParamsState.class);
    }

    @Override
    public @NotNull Data getState() {
        return data;
    }

    @Override
    public void loadState(@NotNull Data state) {
        data = state;
    }

    List<Param> params() {
        return data.params;
    }

    /** Another module or folder starts its progress from scratch. */
    void trackModule(String module) {
        if (!module.equals(data.progressModule)) {
            data.progressModule = module;
            resetProgress();
        }
    }

    void resetProgress() {
        data.passed.clear();
        data.failed.clear();
        data.pending.clear();
    }

    /** The latest result of a test wins. A real result of a parametrized test ('test_z[1]') replaces the status
     * set by hand for the whole test ('test_z'). */
    void record(String test, boolean passed) {
        forget(test);
        int param = test.indexOf('[');
        if (param > 0) {
            forget(test.substring(0, param));
        }
        (passed ? data.passed : data.failed).add(test);
    }

    /** A status set by hand in the module list: passed leaves the clean-up rerun, not run and failed join it. */
    void setStatus(String test, TestStatus status) {
        switch (status) {
            case PASSED -> record(test, true);
            case FAILED -> record(test, false);
            case NOT_RUN -> {
                forget(test);
                data.pending.add(test);
            }
        }
    }

    private void forget(String test) {
        data.passed.remove(test);
        data.failed.remove(test);
        data.pending.remove(test);
    }

    /** pytest arguments from the included rows: standard first, then custom. */
    String arguments() {
        List<String> tokens = new ArrayList<>();
        for (boolean standard : new boolean[]{true, false}) {
            for (Param param : data.params) {
                if (param.included() && param.standard == standard && !param.name.isBlank()) {
                    tokens.addAll(param.tokens());
                }
            }
        }
        return ParametersListUtil.join(tokens);
    }

    /** An option with the same name is not duplicated: it is enabled and takes the new value, title or choices. */
    void add(List<Param> incoming) {
        for (Param param : incoming) {
            Param same = data.params.stream().filter(p -> p.name.equals(param.name)).findFirst().orElse(null);
            if (same == null) {
                data.params.add(param);
                continue;
            }
            same.enabled = true;
            if (param.hasTitle()) {
                same.title = param.title;
            }
            if (param.hasChoices()) {
                same.choices = param.choices;
                same.select(0);
            } else if (param.value != null) {
                same.value = param.value;
            }
        }
    }

    /** Old versions kept 'Samsung' and 'Pixel' as separate rows of '--serial' that excluded each other: rows of one
     * option that have a title and a value become one row with a dropdown (the first one that was on is selected). */
    void migratePresets() {
        Map<String, List<Param>> byName = new LinkedHashMap<>();
        for (Param param : data.params) {
            if (param.hasTitle() && !param.hasChoices() && !param.binary && param.value != null) {
                byName.computeIfAbsent(param.name, name -> new ArrayList<>()).add(param);
            }
        }
        for (List<Param> group : byName.values()) {
            if (group.size() < 2) {
                continue;
            }
            Param merged = group.get(0);
            for (Param row : group) {
                merged.choices.add(new Choice(row.title, row.value));
            }
            Param firstOn = group.stream().filter(row -> row.enabled).findFirst().orElse(null);
            merged.enabled = firstOn != null;
            merged.select(firstOn == null ? 0 : group.indexOf(firstOn));
            merged.title = null;
            data.params.removeAll(group.subList(1, group.size()));
        }
    }

    private static final Pattern TITLE = Pattern.compile("^\\s*([^-:\\s][^:]*?)\\s*:\\s*(-.*)$", Pattern.DOTALL);

    /** 'Samsung: --serial=R58M123456A' → ['Samsung', '--serial=R58M123456A']; null — the text has no title
     * (an option text starts with a dash, so '-p no:warnings' is not a title). */
    static String[] splitTitle(String text) {
        Matcher matcher = text == null ? null : TITLE.matcher(text);
        return matcher != null && matcher.matches() ? new String[]{matcher.group(1), matcher.group(2)} : null;
    }

    private static final Pattern CHOICES = Pattern.compile("^\\s*(-{1,2}[A-Za-z][\\w-]*)\\s*=\\s*\\{(.*)\\}\\s*$", Pattern.DOTALL);

    /** The text of the add dialog: like {@link #parse}; 'Title: --option=value' gives the one option a title;
     * '[Title:] --serial={Samsung: R58M123456A, Pixel: 1A2B3C4D5E6F}' makes one option with a dropdown of values.
     * A title with several options is not applied (the caller reports it, see {@link #splitTitle}). */
    static List<Param> parseInput(String text, boolean standard) {
        String[] split = splitTitle(text);
        String body = split == null ? text : split[1];
        Matcher choices = body == null ? null : CHOICES.matcher(body);
        if (choices != null && choices.matches()) {
            List<Choice> values = parseChoices(choices.group(2));
            if (!values.isEmpty()) {
                Param param = new Param(choices.group(1), null, standard);
                param.choices = values;
                param.select(0);
                param.title = split == null ? null : split[0];
                return new ArrayList<>(List.of(param));
            }
        }
        List<Param> params = parse(body, standard);
        if (split != null && params.size() == 1) {
            params.get(0).title = split[0];
        }
        return params;
    }

    /** 'Samsung: R58M123456A, Pixel: 1A2B3C4D5E6F' → two choices; an entry without a colon is a value with no label. */
    static List<Choice> parseChoices(String inner) {
        List<Choice> out = new ArrayList<>();
        for (String entry : inner.split(",")) {
            String text = entry.trim();
            if (text.isEmpty()) {
                continue;
            }
            int colon = text.indexOf(':');
            if (colon < 0) {
                out.add(new Choice(text, text));
            } else {
                String label = text.substring(0, colon).trim();
                String value = text.substring(colon + 1).trim();
                out.add(new Choice(label.isEmpty() ? value : label, value));
            }
        }
        return out;
    }

    /** '--debugging=0 -k "smoke a" -x' → rows; a space-separated value ('-k smoke') sticks to its option. */
    static List<Param> parse(String text, boolean standard) {
        List<Param> out = new ArrayList<>();
        for (String token : ParametersListUtil.parse(text == null ? "" : text)) {
            if (token.startsWith("-")) {
                int eq = token.indexOf('=');
                out.add(eq < 0 ? new Param(token, null, standard)
                        : new Param(token.substring(0, eq), token.substring(eq + 1), standard));
            } else if (!out.isEmpty()) {
                Param last = out.get(out.size() - 1);
                last.value = last.value == null ? token : last.value + " " + token;
            }
        }
        return out;
    }
}
