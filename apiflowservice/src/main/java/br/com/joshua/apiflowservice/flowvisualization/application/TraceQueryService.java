package br.com.joshua.apiflowservice.flowvisualization.application;

import br.com.joshua.apiflowservice.flowtracking.api.InFlightTraces;
import br.com.joshua.apiflowservice.flowtracking.api.TraceRepository;
import br.com.joshua.apiflowservice.flowtracking.domain.FlowTrace;
import br.com.joshua.apiflowservice.flowvisualization.application.model.ComponentStats;
import br.com.joshua.apiflowservice.flowvisualization.application.model.PageResult;
import br.com.joshua.apiflowservice.flowvisualization.application.model.TraceFilter;
import br.com.joshua.apiflowservice.flowvisualization.application.model.TraceMetrics;
import br.com.joshua.apiflowservice.flowvisualization.application.port.out.TraceReadModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Serves the data the dashboard needs. Combines in-flight traces (still in memory) with persisted ones,
 * so the consolidated state of a trace is available at any moment, including right after a reconnect.
 */
@Service
public class TraceQueryService {

    private final TraceReadModel readModel;
    private final InFlightTraces inFlight;
    private final TraceRepository traceRepository;

    public TraceQueryService(TraceReadModel readModel, InFlightTraces inFlight, TraceRepository traceRepository) {
        this.readModel = readModel;
        this.inFlight = inFlight;
        this.traceRepository = traceRepository;
    }

    public record TraceView(FlowTrace trace, boolean live) {
    }

    public record Blueprint(String referenceTraceId, FlowTrace trace) {
    }

    public PageResult<FlowTrace> search(TraceFilter filter, int page, int size) {
        return readModel.search(filter, page, size);
    }

    public Optional<TraceView> findTrace(String traceId) {
        Optional<FlowTrace> live = inFlight.find(traceId);
        if (live.isPresent()) {
            return Optional.of(new TraceView(live.get(), true));
        }
        return readModel.findTrace(traceId).map(trace -> new TraceView(trace, false));
    }

    public List<FlowTrace> liveTraces() {
        List<FlowTrace> traces = new ArrayList<>(inFlight.all());
        traces.sort(Comparator.comparing(FlowTrace::startedAt).reversed());
        return traces;
    }

    /**
     * The most recent successful execution of the same endpoint. Used to show which steps a request
     * would normally run, so the dashboard can mark steps that were not executed after a failure.
     */
    public Optional<Blueprint> blueprint(String httpMethod, String endpoint) {
        return readModel.latestSuccessfulTraceId(httpMethod, endpoint)
                .flatMap(readModel::findTrace)
                .map(trace -> new Blueprint(trace.traceId(), trace));
    }

    /** Most recent executions in chronological order. */
    public List<FlowTrace> history(int limit) {
        List<FlowTrace> latest = new ArrayList<>(readModel.search(TraceFilter.none(), 0, limit).content());
        Collections.reverse(latest);
        return latest;
    }

    public TraceMetrics metrics() {
        return readModel.metrics();
    }

    public List<ComponentStats> componentStats() {
        return readModel.componentStats();
    }

    public Optional<Double> baselineMedianMicros(String component, String operation) {
        return readModel.baselineMedianMicros(component, operation);
    }

    public int inFlightCount() {
        return inFlight.all().size();
    }

    public int clear() {
        return traceRepository.deleteAll();
    }
}
