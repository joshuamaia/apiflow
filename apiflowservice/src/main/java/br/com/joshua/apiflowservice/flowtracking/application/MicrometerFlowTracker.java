package br.com.joshua.apiflowservice.flowtracking.application;

import br.com.joshua.apiflowservice.flowtracking.api.FlowScope;
import br.com.joshua.apiflowservice.flowtracking.api.FlowTracker;
import br.com.joshua.apiflowservice.flowtracking.api.SpanStartHook;
import br.com.joshua.apiflowservice.flowtracking.application.ActiveTrace.ActiveSpan;
import br.com.joshua.apiflowservice.flowtracking.domain.ComponentType;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * {@link FlowTracker} backed by Micrometer: every tracked step is a Micrometer {@link Observation}
 * (producing the {@code flow.step} timer metric) whose tracing span supplies the span and parent ids.
 * When no tracer is available the recorder generates ids and derives the parent from the span stack.
 */
public class MicrometerFlowTracker implements FlowTracker {

    static final String OBSERVATION_NAME = "flow.step";
    private static final Logger log = LoggerFactory.getLogger(MicrometerFlowTracker.class);

    private final FlowRecorder recorder;
    private final ObservationRegistry observationRegistry;
    private final Tracer tracer;
    private final List<SpanStartHook> hooks;

    public MicrometerFlowTracker(FlowRecorder recorder, ObservationRegistry observationRegistry, Tracer tracer) {
        this(recorder, observationRegistry, tracer, List.of());
    }

    public MicrometerFlowTracker(FlowRecorder recorder, ObservationRegistry observationRegistry, Tracer tracer,
                                 List<SpanStartHook> hooks) {
        this.recorder = recorder;
        this.observationRegistry = observationRegistry == null ? ObservationRegistry.NOOP : observationRegistry;
        this.tracer = tracer == null ? Tracer.NOOP : tracer;
        this.hooks = hooks == null ? List.of() : List.copyOf(hooks);
    }

    @Override
    public FlowScope startSpan(String component, String operation, ComponentType type) {
        if (!recorder.isTracing()) {
            return FlowScope.NOOP;
        }
        Observation observation = null;
        Observation.Scope observationScope = null;
        try {
            observation = Observation.createNotStarted(OBSERVATION_NAME, observationRegistry)
                    .contextualName(component + "." + operation)
                    .lowCardinalityKeyValue("flow.component", component)
                    .lowCardinalityKeyValue("flow.operation", operation)
                    .lowCardinalityKeyValue("flow.type", type.name())
                    .start();
            observationScope = observation.openScope();

            String spanId = null;
            String parentSpanId = null;
            Span span = tracer.currentSpan();
            String traceId = recorder.currentTraceId().orElse(null);
            if (span != null && !span.isNoop() && span.context().traceId().equals(traceId)) {
                TraceContext context = span.context();
                spanId = context.spanId();
                parentSpanId = context.parentId();
            }
            ActiveSpan activeSpan = recorder.openSpan(component, operation, type, spanId, parentSpanId);
            FlowScope scope = new ObservedScope(recorder, observation, observationScope, activeSpan);
            if (activeSpan != null) {
                runHooks(component, operation, type);
            }
            return scope;
        } catch (RuntimeException e) {
            log.warn("Could not start flow span {}.{}; continuing untracked", component, operation, e);
            closeQuietly(observation, observationScope);
            return FlowScope.NOOP;
        }
    }

    /** Runs the start hooks inside the new span; a failing hook never affects the step itself. */
    private void runHooks(String component, String operation, ComponentType type) {
        if (hooks.isEmpty()) {
            return;
        }
        SpanStartHook.StartedStep step = new SpanStartHook.StartedStep() {
            @Override
            public String component() {
                return component;
            }

            @Override
            public String operation() {
                return operation;
            }

            @Override
            public ComponentType type() {
                return type;
            }

            @Override
            public void tag(String key, String value) {
                MicrometerFlowTracker.this.tag(key, value);
            }
        };
        for (SpanStartHook hook : hooks) {
            try {
                hook.onSpanStarted(step);
            } catch (RuntimeException e) {
                log.warn("Span start hook {} failed on {}.{}", hook.getClass().getSimpleName(), component, operation, e);
            }
        }
    }

    @Override
    public boolean isTracing() {
        return recorder.isTracing();
    }

    @Override
    public Optional<String> currentTraceId() {
        return recorder.currentTraceId();
    }

    @Override
    public void tag(String key, String value) {
        try {
            recorder.tag(key, value);
        } catch (RuntimeException e) {
            log.warn("Could not tag flow span", e);
        }
    }

    private static void closeQuietly(Observation observation, Observation.Scope scope) {
        try {
            if (scope != null) {
                scope.close();
            }
            if (observation != null) {
                observation.stop();
            }
        } catch (RuntimeException ignored) {
            // best effort cleanup
        }
    }

    private static final class ObservedScope implements FlowScope {
        private final FlowRecorder recorder;
        private final Observation observation;
        private final Observation.Scope observationScope;
        private final ActiveSpan span;
        private Throwable error;
        private boolean closed;

        private ObservedScope(FlowRecorder recorder, Observation observation, Observation.Scope observationScope,
                              ActiveSpan span) {
            this.recorder = recorder;
            this.observation = observation;
            this.observationScope = observationScope;
            this.span = span;
        }

        @Override
        public void markError(Throwable error) {
            this.error = error;
            try {
                observation.error(error);
            } catch (RuntimeException e) {
                log.debug("Could not flag observation error", e);
            }
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            try {
                recorder.closeSpan(span, error);
            } catch (RuntimeException e) {
                log.warn("Could not close flow span", e);
            }
            closeQuietly(observation, observationScope);
        }
    }
}
