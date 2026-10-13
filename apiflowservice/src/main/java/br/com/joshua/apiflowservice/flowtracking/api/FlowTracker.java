package br.com.joshua.apiflowservice.flowtracking.api;

import br.com.joshua.apiflowservice.flowtracking.domain.ComponentType;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Span creation contract. Used by the {@code @FlowTrack} aspect and directly by code that needs explicit
 * instrumentation (database calls, self-invocations, code not managed by Spring).
 * <p>
 * Every method is a no-op when the current thread is not serving a tracked request, and instrumentation
 * failures are contained: they are logged and never propagate to business code.
 */
public interface FlowTracker {

    /** Opens a child span of the currently open span (or a root span of the current trace). */
    FlowScope startSpan(String component, String operation, ComponentType type);

    /** {@code true} when the current thread is serving a tracked request. */
    boolean isTracing();

    /** Trace id of the request being served by the current thread, if tracked. */
    Optional<String> currentTraceId();

    /**
     * Attaches a small, non-sensitive attribute to the innermost open span.
     * Never pass user input, credentials or personal data.
     */
    void tag(String key, String value);

    default <T> T track(String component, String operation, ComponentType type, Supplier<T> call) {
        FlowScope scope = startSpan(component, operation, type);
        try {
            return call.get();
        } catch (RuntimeException | Error e) {
            scope.markError(e);
            throw e;
        } finally {
            scope.close();
        }
    }

    default void track(String component, String operation, ComponentType type, Runnable call) {
        track(component, operation, type, () -> {
            call.run();
            return null;
        });
    }
}
