package dev.autotests.runtoggles;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import com.intellij.util.execution.ParametersListUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** Panel rows (stored in .idea/workspace.xml): a pytest option, on/off, standard/custom block. */
@Service(Service.Level.PROJECT)
@State(name = "AtRunParams", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public final class RunParamsState implements PersistentStateComponent<RunParamsState.Data> {

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

        /** Checkbox label: a 0/1 option without its value ('--debugging'), others as in the arguments. */
        String label() {
            return binary ? name : String.join(" ", tokens());
        }
    }

    public static final class Data {
        public List<Param> params = new ArrayList<>();
        public boolean seeded;
        /** Clean up module: module runs skip tests that already passed (off by default) */
        public boolean cleanup;
        /** absolute path of the test module whose progress is counted */
        public String progressModule;
        /** results in the tracked module: 'test_x', 'TestClass::test_y', 'test_z[1]' */
        public List<String> passed = new ArrayList<>();
        public List<String> failed = new ArrayList<>();
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

    /** Another module starts its progress from scratch. */
    void trackModule(String module) {
        if (!module.equals(data.progressModule)) {
            data.progressModule = module;
            resetProgress();
        }
    }

    void resetProgress() {
        data.passed.clear();
        data.failed.clear();
    }

    /** The latest result of a test wins. */
    void record(String test, boolean passed) {
        data.passed.remove(test);
        data.failed.remove(test);
        (passed ? data.passed : data.failed).add(test);
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

    /** An option with the same name is not duplicated: it is enabled and takes the new value. */
    void add(List<Param> incoming) {
        for (Param param : incoming) {
            Param same = data.params.stream().filter(p -> p.name.equals(param.name)).findFirst().orElse(null);
            if (same == null) {
                data.params.add(param);
            } else {
                same.enabled = true;
                if (param.value != null) {
                    same.value = param.value;
                }
            }
        }
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
