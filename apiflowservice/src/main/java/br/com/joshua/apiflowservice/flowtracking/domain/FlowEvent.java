package br.com.joshua.apiflowservice.flowtracking.domain;

import java.time.Instant;

/**
 * Lifecycle notification emitted by the tracker. Every event belongs to exactly one {@code traceId},
 * which lets consumers keep concurrent requests apart.
 *
 * @param sequence monotonically increasing number across all traces (usable as SSE event id)
 * @param trace    trace summary (without spans) – present for TRACE_* events
 * @param span     span snapshot – present for SPAN_* events
 */
public record FlowEvent(
        long sequence,
        FlowEventType type,
        String traceId,
        Instant timestamp,
        FlowTrace trace,
        FlowSpan span) {
}
