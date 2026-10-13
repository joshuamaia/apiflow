package br.com.joshua.apiflowservice.flowvisualization.application.port.out;

import br.com.joshua.apiflowservice.flowtracking.domain.FlowTrace;
import br.com.joshua.apiflowservice.flowvisualization.application.model.ComponentStats;
import br.com.joshua.apiflowservice.flowvisualization.application.model.PageResult;
import br.com.joshua.apiflowservice.flowvisualization.application.model.TraceFilter;
import br.com.joshua.apiflowservice.flowvisualization.application.model.TraceMetrics;

import java.util.List;
import java.util.Optional;

/** Query side over the traces persisted by the tracking module. */
public interface TraceReadModel {

    /** Trace summaries (without spans), newest first. */
    PageResult<FlowTrace> search(TraceFilter filter, int page, int size);

    /** Trace with all its spans ordered by start sequence. */
    Optional<FlowTrace> findTrace(String traceId);

    Optional<String> latestSuccessfulTraceId(String httpMethod, String endpoint);

    TraceMetrics metrics();

    List<ComponentStats> componentStats();

    /**
     * Median duration of a component operation over completed spans, ignoring spans that carry an
     * artificial demo delay. Represents a "normal" execution.
     */
    Optional<Double> baselineMedianMicros(String component, String operation);
}
