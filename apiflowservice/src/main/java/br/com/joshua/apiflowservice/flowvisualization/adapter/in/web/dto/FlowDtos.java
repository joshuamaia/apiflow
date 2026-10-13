package br.com.joshua.apiflowservice.flowvisualization.adapter.in.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Response DTOs of the visualization API. Durations are exposed in milliseconds with microsecond
 * precision.
 */
public final class FlowDtos {

    private FlowDtos() {
    }

    public record SpanDto(
            String traceId,
            String spanId,
            String parentSpanId,
            String component,
            String operation,
            String type,
            int sequence,
            Instant startedAt,
            Instant finishedAt,
            Double durationMs,
            String status,
            boolean slow,
            String errorType,
            String errorMessage,
            Long artificialDelayMs,
            Map<String, String> tags) {
    }

    public record TraceSummaryDto(
            String traceId,
            String httpMethod,
            String endpoint,
            Integer httpStatus,
            String scenario,
            Instant startedAt,
            Instant finishedAt,
            Double durationMs,
            String status,
            int spanCount,
            String errorMessage,
            boolean truncated) {
    }

    public record TraceDetailDto(TraceSummaryDto trace, List<SpanDto> spans, boolean live, double slowThresholdMs) {
    }

    public record SpanDetailDto(SpanDto span, List<SpanDto> ancestors, List<SpanDto> children,
                                Double baselineMedianMs, Double deltaFromBaselineMs) {
    }

    public record BlueprintDto(String referenceTraceId, String httpMethod, String endpoint, List<SpanDto> spans) {
    }

    public record MetricsDto(
            long totalRequests,
            long successfulRequests,
            long failedRequests,
            double avgLatencyMs,
            double maxLatencyMs,
            double p95LatencyMs,
            long totalSpans,
            int inFlight,
            double slowThresholdMs) {
    }

    public record ComponentMetricsDto(String component, String operation, String type, long executions, long errors,
                                      double avgMs, double medianMs, double maxMs) {
    }

    public record HistoryPointDto(String traceId, Instant startedAt, Double durationMs, String status,
                                  Integer httpStatus, String httpMethod, String endpoint, String scenario) {
    }

    public record PageDto<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    }

    public record FlowEventDto(long sequence, String type, String traceId, Instant timestamp,
                               TraceSummaryDto trace, SpanDto span) {
    }

    public record ClearResultDto(int deletedTraces) {
    }
}
