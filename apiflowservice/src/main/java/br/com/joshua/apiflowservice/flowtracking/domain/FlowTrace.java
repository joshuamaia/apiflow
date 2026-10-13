package br.com.joshua.apiflowservice.flowtracking.domain;

import java.time.Instant;
import java.util.List;

/**
 * Immutable snapshot of one tracked HTTP request and the steps it executed.
 *
 * @param endpoint  route pattern (e.g. {@code /api/orders/{id}}) – never the raw URI or query string
 * @param scenario  optional demo scenario label supplied by the caller
 * @param spanCount number of recorded spans (also available when {@code spans} is not loaded)
 * @param spans     recorded spans; empty for summaries
 * @param truncated {@code true} when the span limit per trace was reached and later spans were dropped
 */
public record FlowTrace(
        String traceId,
        String httpMethod,
        String endpoint,
        Integer httpStatus,
        String scenario,
        Instant startedAt,
        Instant finishedAt,
        Long durationMicros,
        TraceStatus status,
        String errorMessage,
        int spanCount,
        List<FlowSpan> spans,
        boolean truncated) {

    public FlowTrace {
        spans = spans == null ? List.of() : List.copyOf(spans);
    }

    /** Same trace without span payload, used for lightweight events. */
    public FlowTrace withoutSpans() {
        return new FlowTrace(traceId, httpMethod, endpoint, httpStatus, scenario, startedAt, finishedAt,
                durationMicros, status, errorMessage, spanCount, List.of(), truncated);
    }
}
