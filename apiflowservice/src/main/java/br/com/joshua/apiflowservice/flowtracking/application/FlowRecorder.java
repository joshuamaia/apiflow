package br.com.joshua.apiflowservice.flowtracking.application;

import br.com.joshua.apiflowservice.flowtracking.api.FlowEventPublisher;
import br.com.joshua.apiflowservice.flowtracking.api.InFlightTraces;
import br.com.joshua.apiflowservice.flowtracking.application.ActiveTrace.ActiveSpan;
import br.com.joshua.apiflowservice.flowtracking.domain.ComponentType;
import br.com.joshua.apiflowservice.flowtracking.domain.ErrorSanitizer;
import br.com.joshua.apiflowservice.flowtracking.domain.FlowEvent;
import br.com.joshua.apiflowservice.flowtracking.domain.FlowEventType;
import br.com.joshua.apiflowservice.flowtracking.domain.FlowSpan;
import br.com.joshua.apiflowservice.flowtracking.domain.FlowTrace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * Core of the tracking mechanism: correlates spans with the request being served by the current thread,
 * emits lifecycle events and hands finished traces to the asynchronous writer.
 * <p>
 * Memory is bounded: at most {@code maxInFlightTraces} traces are kept (running or waiting to be
 * persisted) and at most {@code maxSpansPerTrace} spans per trace.
 */
public class FlowRecorder implements InFlightTraces {

    private static final Logger log = LoggerFactory.getLogger(FlowRecorder.class);
    private static final int MAX_TAGS_PER_SPAN = 8;
    private static final int MAX_TAG_LENGTH = 80;

    private final ThreadLocal<ActiveTrace> current = new ThreadLocal<>();
    private final Map<String, ActiveTrace> inFlight = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();
    private final AtomicLong droppedTraces = new AtomicLong();

    private final FlowEventPublisher publisher;
    private final AsyncTraceWriter writer;
    private final Clock clock;
    private final LongSupplier ticker;
    private final int maxInFlightTraces;
    private final int maxSpansPerTrace;

    public FlowRecorder(FlowEventPublisher publisher, AsyncTraceWriter writer, Clock clock, LongSupplier ticker,
                        int maxInFlightTraces, int maxSpansPerTrace) {
        this.publisher = publisher;
        this.writer = writer;
        this.clock = clock;
        this.ticker = ticker;
        this.maxInFlightTraces = maxInFlightTraces;
        this.maxSpansPerTrace = maxSpansPerTrace;
    }

    /**
     * Starts tracking the request served by the current thread.
     *
     * @return the trace, or empty when tracking is not possible (nested call, duplicated id, capacity reached)
     */
    public Optional<ActiveTrace> beginTrace(String traceId, String httpMethod, String path, String scenario) {
        if (current.get() != null) {
            return Optional.empty();
        }
        if (inFlight.size() >= maxInFlightTraces) {
            droppedTraces.incrementAndGet();
            log.debug("Flow tracking capacity reached ({} traces); request not tracked", maxInFlightTraces);
            return Optional.empty();
        }
        ActiveTrace trace = new ActiveTrace(traceId, httpMethod, path, scenario, clock.instant(), ticker.getAsLong());
        if (inFlight.putIfAbsent(traceId, trace) != null) {
            return Optional.empty();
        }
        current.set(trace);
        publish(FlowEventType.TRACE_STARTED, traceId, trace.summary(), null);
        return Optional.of(trace);
    }

    public boolean isTracing() {
        return current.get() != null;
    }

    public Optional<String> currentTraceId() {
        ActiveTrace trace = current.get();
        return trace == null ? Optional.empty() : Optional.of(trace.traceId());
    }

    /**
     * Opens a span inside the current trace.
     *
     * @param spanId       id provided by the tracing system, or {@code null} to generate one
     * @param parentSpanId parent provided by the tracing system, or {@code null} to use the innermost open span
     * @return the span, or {@code null} when not tracing or the span limit was reached
     */
    public ActiveSpan openSpan(String component, String operation, ComponentType type, String spanId, String parentSpanId) {
        ActiveTrace trace = current.get();
        if (trace == null) {
            return null;
        }
        String id = spanId != null ? spanId : IdGenerator.spanId();
        ActiveSpan span = trace.openSpan(id, parentSpanId, component, operation, type,
                ticker.getAsLong(), maxSpansPerTrace);
        if (span != null) {
            publish(FlowEventType.SPAN_STARTED, trace.traceId(), null, span.snapshot());
        }
        return span;
    }

    public void closeSpan(ActiveSpan span, Throwable error) {
        if (span == null) {
            return;
        }
        long nanos = ticker.getAsLong();
        FlowSpan finished = span.trace().closeSpan(span, nanos, error,
                ErrorSanitizer.type(error), ErrorSanitizer.message(error));
        publish(error == null ? FlowEventType.SPAN_COMPLETED : FlowEventType.SPAN_FAILED,
                finished.traceId(), null, finished);
    }

    public void tag(String key, String value) {
        ActiveTrace trace = current.get();
        if (trace == null || key == null || value == null) {
            return;
        }
        trace.tagInnermost(truncate(key), truncate(value), MAX_TAGS_PER_SPAN);
    }

    /**
     * Finishes the trace of the current thread and schedules it for persistence. Always clears the
     * thread-bound state, even if something goes wrong.
     */
    public void endTrace(ActiveTrace trace, int httpStatus, String endpoint, Throwable error) {
        current.remove();
        FlowTrace finished = trace.finish(ticker.getAsLong(), httpStatus, endpoint,
                ErrorSanitizer.message(error));
        publish(FlowEventType.TRACE_COMPLETED, finished.traceId(), finished.withoutSpans(), null);
        boolean accepted = writer.submit(finished, persisted -> afterPersistence(finished, persisted));
        if (!accepted) {
            inFlight.remove(finished.traceId());
        }
    }

    private void afterPersistence(FlowTrace trace, boolean persisted) {
        inFlight.remove(trace.traceId());
        if (persisted) {
            publish(FlowEventType.TRACE_PERSISTED, trace.traceId(), trace.withoutSpans(), null);
        }
    }

    @Override
    public Optional<FlowTrace> find(String traceId) {
        ActiveTrace trace = inFlight.get(traceId);
        return trace == null ? Optional.empty() : Optional.of(trace.snapshot());
    }

    @Override
    public List<FlowTrace> all() {
        List<FlowTrace> result = new ArrayList<>();
        for (ActiveTrace trace : inFlight.values()) {
            result.add(trace.summary());
        }
        return result;
    }

    public long droppedTraces() {
        return droppedTraces.get();
    }

    public int inFlightCount() {
        return inFlight.size();
    }

    private void publish(FlowEventType type, String traceId, FlowTrace trace, FlowSpan span) {
        try {
            publisher.publish(new FlowEvent(sequence.incrementAndGet(), type, traceId, clock.instant(), trace, span));
        } catch (RuntimeException e) {
            log.warn("Failed to publish flow event {} for trace {}", type, traceId, e);
        }
    }

    private static String truncate(String value) {
        return value.length() > MAX_TAG_LENGTH ? value.substring(0, MAX_TAG_LENGTH) : value;
    }
}
