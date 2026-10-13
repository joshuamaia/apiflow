package br.com.joshua.apiflowservice.flowvisualization.application.model;

/** Aggregates over all stored traces. Durations in microseconds. */
public record TraceMetrics(long total, long successful, long failed, double avgDurationMicros,
                           long maxDurationMicros, double p95DurationMicros, long totalSpans) {
}
