package br.com.joshua.apiflowservice.flowtracking.application;

import br.com.joshua.apiflowservice.flowtracking.api.FlowScope;
import br.com.joshua.apiflowservice.flowtracking.api.FlowTracker;
import br.com.joshua.apiflowservice.flowtracking.application.ActiveTrace.ActiveSpan;
import br.com.joshua.apiflowservice.flowtracking.domain.ComponentType;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    public MicrometerFlowTracker(FlowRecorder recorder, ObservationRegistry observationRegistry, Tracer tracer) {
        this.recorder = recorder;
        this.observationRegistry = observationRegistry == null ? ObservationRegistry.NOOP : observationRegistry;
        this.tracer = tracer == null ? Tracer.NOOP : tracer;
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
            return new ObservedScope(recorder, observation, observationScope, activeSpan);
        } catch (RuntimeException e) {
            log.warn("Could not start flow span {}.{}; continuing untracked", component, operation, e);
            closeQuietly(observation, observationScope);
            return FlowScope.NOOP;
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
