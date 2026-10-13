package br.com.joshua.apiflowservice.flowvisualization.application.model;

import br.com.joshua.apiflowservice.flowtracking.domain.ComponentType;

/** Aggregates of one component operation across stored spans. Durations in microseconds. */
public record ComponentStats(String component, String operation, ComponentType type, long count, long errors,
                             double avgDurationMicros, double medianDurationMicros, long maxDurationMicros) {
}
