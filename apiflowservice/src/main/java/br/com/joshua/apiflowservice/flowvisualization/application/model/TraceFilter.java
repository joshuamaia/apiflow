package br.com.joshua.apiflowservice.flowvisualization.application.model;

import br.com.joshua.apiflowservice.flowtracking.domain.TraceStatus;

import java.time.Instant;

/** Optional criteria to search stored traces; {@code null} fields are ignored. */
public record TraceFilter(TraceStatus status, String httpMethod, String endpoint, String scenario,
                          Instant from, Instant to, Long minDurationMs) {

    public static TraceFilter none() {
        return new TraceFilter(null, null, null, null, null, null, null);
    }
}
