package br.com.joshua.apiflowservice.flowtracking.application;

import br.com.joshua.apiflowservice.flowtracking.domain.ComponentType;
import br.com.joshua.apiflowservice.flowtracking.domain.FlowSpan;
import br.com.joshua.apiflowservice.flowtracking.domain.FlowTrace;
import br.com.joshua.apiflowservice.flowtracking.domain.SpanStatus;
import br.com.joshua.apiflowservice.flowtracking.domain.TraceStatus;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Mutable state of a request being tracked. Written by the request thread, read by query threads –
 * every access goes through the instance monitor.
 * <p>
 * Durations come from a monotonic clock ({@code System.nanoTime}). Span timestamps are derived from the
 * trace start instant plus the monotonic offset, so they are consistent with the durations and keep
 * microsecond precision even where the wall clock is coarse.
 */
public final class ActiveTrace {

    private final String traceId;
    private final String httpMethod;
    private final String scenario;
    private final Instant startedAt;
    private final long startNanos;
    private final List<ActiveSpan> spans = new ArrayList<>();
    private final Deque<ActiveSpan> openSpans = new ArrayDeque<>();

    private String endpoint;
    private Instant finishedAt;
    private Long durationMicros;
    private Integer httpStatus;
    private TraceStatus status = TraceStatus.RUNNING;
    private String errorMessage;
    private boolean truncated;

    ActiveTrace(String traceId, String httpMethod, String endpoint, String scenario, Instant startedAt, long startNanos) {
        this.traceId = traceId;
        this.httpMethod = httpMethod;
        this.endpoint = endpoint;
        this.scenario = scenario;
        this.startedAt = startedAt;
        this.startNanos = startNanos;
    }

    public String traceId() {
        return traceId;
    }

    synchronized ActiveSpan openSpan(String spanId, String parentSpanId, String component, String operation,
                                     ComponentType type, long nanos, int maxSpans) {
        if (spans.size() >= maxSpans) {
            truncated = true;
            return null;
        }
        String parent = parentSpanId;
        if (parent == null && !openSpans.isEmpty()) {
            parent = openSpans.peek().spanId;
        }
        ActiveSpan span = new ActiveSpan(this, spanId, parent, component, operation, type, spans.size(),
                instantAt(nanos), nanos);
        spans.add(span);
        openSpans.push(span);
        return span;
    }

    synchronized FlowSpan closeSpan(ActiveSpan span, long nanos, Throwable error, String errorType,
                                    String errorMessage) {
        span.finish(instantAt(nanos), micros(span.startNanos, nanos),
                error == null ? SpanStatus.COMPLETED : SpanStatus.ERROR, errorType, errorMessage);
        openSpans.remove(span);
        return span.snapshot();
    }

    synchronized void tagInnermost(String key, String value, int maxTags) {
        ActiveSpan span = openSpans.peek();
        if (span != null && (span.tags.size() < maxTags || span.tags.containsKey(key))) {
            span.tags.put(key, value);
        }
    }

    synchronized FlowTrace finish(long nanos, int httpStatus, String endpoint, String errorMessage) {
        // Spans still open here were not closed by their instrumentation (should not happen with
        // try/finally); they are reported as errors instead of silently disappearing.
        for (ActiveSpan open : openSpans) {
            open.finish(instantAt(nanos), micros(open.startNanos, nanos), SpanStatus.ERROR, "IncompleteSpan",
                    "Span was not closed before the response was sent");
        }
        openSpans.clear();
        if (endpoint != null) {
            this.endpoint = endpoint;
        }
        this.finishedAt = instantAt(nanos);
        this.durationMicros = micros(startNanos, nanos);
        this.httpStatus = httpStatus;
        this.status = TraceStatus.fromHttpStatus(httpStatus);
        this.errorMessage = errorMessage != null || status != TraceStatus.ERROR ? errorMessage : failureOrigin();
        return snapshot();
    }

    /**
     * Message of the failed span where the error originated: the deepest failed span, i.e. one without
     * failed children. Exceptions handled by {@code @ControllerAdvice} never reach the servlet filter, so
     * this is how the trace learns why it failed.
     */
    private String failureOrigin() {
        ActiveSpan origin = null;
        for (ActiveSpan span : spans) {
            if (span.status == SpanStatus.ERROR) {
                origin = span;
            }
        }
        return origin == null ? null : origin.errorMessage;
    }

    synchronized FlowTrace snapshot() {
        List<FlowSpan> spanSnapshots = new ArrayList<>(spans.size());
        for (ActiveSpan span : spans) {
            spanSnapshots.add(span.snapshot());
        }
        return new FlowTrace(traceId, httpMethod, endpoint, httpStatus, scenario, startedAt, finishedAt,
                durationMicros, status, errorMessage, spans.size(), spanSnapshots, truncated);
    }

    synchronized FlowTrace summary() {
        return new FlowTrace(traceId, httpMethod, endpoint, httpStatus, scenario, startedAt, finishedAt,
                durationMicros, status, errorMessage, spans.size(), List.of(), truncated);
    }

    private Instant instantAt(long nanos) {
        return startedAt.plusNanos(TimeUnit.MICROSECONDS.toNanos(micros(startNanos, nanos)));
    }

    static long micros(long fromNanos, long toNanos) {
        return TimeUnit.NANOSECONDS.toMicros(Math.max(0, toNanos - fromNanos));
    }

    /** A span of an {@link ActiveTrace}. All fields are guarded by the owning trace's monitor. */
    public static final class ActiveSpan {
        private final ActiveTrace trace;
        private final String spanId;
        private final String parentSpanId;
        private final String component;
        private final String operation;
        private final ComponentType type;
        private final int sequence;
        private final Instant startedAt;
        private final long startNanos;
        private final Map<String, String> tags = new LinkedHashMap<>();
        private Instant finishedAt;
        private Long durationMicros;
        private SpanStatus status = SpanStatus.RUNNING;
        private String errorType;
        private String errorMessage;

        private ActiveSpan(ActiveTrace trace, String spanId, String parentSpanId, String component, String operation,
                           ComponentType type, int sequence, Instant startedAt, long startNanos) {
            this.trace = trace;
            this.spanId = spanId;
            this.parentSpanId = parentSpanId;
            this.component = component;
            this.operation = operation;
            this.type = type;
            this.sequence = sequence;
            this.startedAt = startedAt;
            this.startNanos = startNanos;
        }

        private void finish(Instant finishedAt, long durationMicros, SpanStatus status, String errorType,
                            String errorMessage) {
            this.finishedAt = finishedAt;
            this.durationMicros = durationMicros;
            this.status = status;
            this.errorType = errorType;
            this.errorMessage = errorMessage;
        }

        ActiveTrace trace() {
            return trace;
        }

        public String spanId() {
            return spanId;
        }

        FlowSpan snapshot() {
            return new FlowSpan(trace.traceId, spanId, parentSpanId, component, operation, type, sequence, startedAt,
                    finishedAt, durationMicros, status, errorType, errorMessage, tags);
        }
    }
}
