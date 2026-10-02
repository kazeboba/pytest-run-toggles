package dev.autotests.runtoggles;

import com.intellij.util.messages.Topic;

/** Options changed: the panel, the Run tab toolbar and the status bar refresh. source — who changed them (or null). */
public interface RunParamsTopic {

    Topic<RunParamsTopic> TOPIC = Topic.create("Pytest params changed", RunParamsTopic.class);

    void changed(Object source);
}
