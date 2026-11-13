package dev.autotests.runtoggles;

import com.intellij.util.execution.ParametersListUtil;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** A saved configuration's own Additional Arguments seen through a panel option: state, toggle, label.
 * Only the option's tokens are touched — positional arguments and unknown options stay as they are. */
final class ConfigArgs {

    private ConfigArgs() {
    }

    /** A 0/1 option — by its value (absent: the conftest default); others — present or not. */
    static boolean isOn(@Nullable String args, RunParamsState.Param param) {
        List<String> tokens = tokens(args);
        int index = find(tokens, param.name);
        if (param.binary) {
            return "1".equals(index < 0 ? param.conftestDefault : valueAt(tokens, index, param));
        }
        return index >= 0;
    }

    /** A 0/1 option gets an explicit value in place; another option is removed, or added with the panel value
     * (an option already present keeps its own value). */
    static String toggle(@Nullable String args, RunParamsState.Param param, boolean on) {
        List<String> tokens = tokens(args);
        int index = find(tokens, param.name);
        if (param.binary) {
            if (index >= 0) {
                remove(tokens, index, param);
            }
            tokens.add(index >= 0 ? index : tokens.size(), param.name + "=" + (on ? "1" : "0"));
        } else if (on && index < 0) {
            tokens.addAll(param.tokens());
        } else if (!on && index >= 0) {
            remove(tokens, index, param);
        }
        return ParametersListUtil.join(tokens);
    }

    /** The option as the configuration passes it ('maxfail=1', '-k smoke'); an option with a title keeps it;
     * null — not passed. */
    static @Nullable String label(@Nullable String args, RunParamsState.Param param) {
        if (param.hasTitle()) {
            return param.title;
        }
        List<String> tokens = tokens(args);
        int index = find(tokens, param.name);
        if (index < 0) {
            return null;
        }
        String value = param.binary ? null : valueAt(tokens, index, param);
        String text = value == null ? param.name : tokens.get(index).contains("=")
                ? param.name + "=" + value : param.name + " " + value;
        return text.startsWith("--") ? text.substring(2) : text;
    }

    private static List<String> tokens(@Nullable String args) {
        return new ArrayList<>(ParametersListUtil.parse(args == null ? "" : args));
    }

    private static int find(List<String> tokens, String name) {
        for (int i = 0; i < tokens.size(); i++) {
            if (tokens.get(i).equals(name) || tokens.get(i).startsWith(name + "=")) {
                return i;
            }
        }
        return -1;
    }

    /** 'name=value', or 'name value' when the option takes a value (the panel row has one or it is 0/1). */
    private static @Nullable String valueAt(List<String> tokens, int index, RunParamsState.Param param) {
        String token = tokens.get(index);
        int eq = token.indexOf('=');
        if (eq >= 0) {
            return token.substring(eq + 1);
        }
        return takesValue(param) && hasValueToken(tokens, index) ? tokens.get(index + 1) : null;
    }

    private static void remove(List<String> tokens, int index, RunParamsState.Param param) {
        boolean separateValue = !tokens.get(index).contains("=") && takesValue(param) && hasValueToken(tokens, index);
        tokens.remove(index);
        if (separateValue) {
            tokens.remove(index);
        }
    }

    private static boolean takesValue(RunParamsState.Param param) {
        return param.binary || param.value != null;
    }

    private static boolean hasValueToken(List<String> tokens, int index) {
        return index + 1 < tokens.size() && !tokens.get(index + 1).startsWith("-");
    }
}
