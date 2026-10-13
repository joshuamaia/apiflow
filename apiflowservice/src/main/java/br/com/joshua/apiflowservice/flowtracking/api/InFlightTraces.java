package br.com.joshua.apiflowservice.flowtracking.api;

import br.com.joshua.apiflowservice.flowtracking.domain.FlowTrace;

import java.util.List;
import java.util.Optional;

/**
 * Read access to traces that are still running or not yet persisted. Lets consumers rebuild the
 * consolidated state of a trace after a reconnection.
 */
public interface InFlightTraces {

    Optional<FlowTrace> find(String traceId);

    List<FlowTrace> all();
}
