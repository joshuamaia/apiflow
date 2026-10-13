package br.com.joshua.apiflowservice.flowtracking.api;

import br.com.joshua.apiflowservice.flowtracking.domain.ComponentType;

/**
 * Extension point invoked on the request thread right after a step (span) starts, before the step's own
 * code runs. Whatever the hook does is therefore measured as part of that step.
 * <p>
 * Meant for opt-in tooling such as demo fault/latency injection. Hooks must be fast unless they are
 * deliberately slowing the step down; exceptions thrown by a hook are logged and ignored.
 */
public interface SpanStartHook {

    void onSpanStarted(StartedStep step);

    /** The step that just started. */
    interface StartedStep {

        String component();

        String operation();

        ComponentType type();

        /** Adds a small, non-sensitive attribute to this step. */
        void tag(String key, String value);
    }
}
