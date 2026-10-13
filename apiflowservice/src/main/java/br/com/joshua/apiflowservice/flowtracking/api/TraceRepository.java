package br.com.joshua.apiflowservice.flowtracking.api;

import br.com.joshua.apiflowservice.flowtracking.domain.FlowTrace;

import java.time.Instant;

/**
 * Trace persistence contract (write side). Called from a background worker, never from the request
 * thread.
 */
public interface TraceRepository {

    void save(FlowTrace trace);

    /**
     * Keeps at most {@code maxTraces} traces and removes those started before {@code cutoff}.
     *
     * @return number of removed traces
     */
    int applyRetention(int maxTraces, Instant cutoff);

    /** Removes every stored trace. @return number of removed traces */
    int deleteAll();
}
