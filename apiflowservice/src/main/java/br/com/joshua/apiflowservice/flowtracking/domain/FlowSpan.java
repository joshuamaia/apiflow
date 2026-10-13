package br.com.joshua.apiflowservice.flowtracking.domain;

import java.time.Instant;
import java.util.Map;

/**
 * Immutable snapshot of one tracked step inside a request.
 *
 * @param sequence       order in which the span was started inside its trace (0-based)
 * @param durationMicros measured with a monotonic clock; {@code null} while the span is running
 * @param tags           small set of safe, explicitly added attributes (never method arguments)
 */
public record FlowSpan(
        String traceId,
        String spanId,
        String parentSpanId,
        String component,
        String operation,
        ComponentType type,
        int sequence,
        Instant startedAt,
        Instant finishedAt,
        Long durationMicros,
        SpanStatus status,
        String errorType,
        String errorMessage,
        Map<String, String> tags) {

    public FlowSpan {
        tags = tags == null ? Map.of() : Map.copyOf(tags);
    }

    public boolean isRoot() {
        return parentSpanId == null;
    }
}
