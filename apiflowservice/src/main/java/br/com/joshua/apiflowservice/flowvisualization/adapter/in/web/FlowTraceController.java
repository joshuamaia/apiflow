package br.com.joshua.apiflowservice.flowvisualization.adapter.in.web;

import br.com.joshua.apiflowservice.flowtracking.domain.FlowSpan;
import br.com.joshua.apiflowservice.flowtracking.domain.FlowTrace;
import br.com.joshua.apiflowservice.flowtracking.domain.TraceStatus;
import br.com.joshua.apiflowservice.flowvisualization.adapter.in.web.dto.FlowDtos.BlueprintDto;
import br.com.joshua.apiflowservice.flowvisualization.adapter.in.web.dto.FlowDtos.ClearResultDto;
import br.com.joshua.apiflowservice.flowvisualization.adapter.in.web.dto.FlowDtos.ComponentMetricsDto;
import br.com.joshua.apiflowservice.flowvisualization.adapter.in.web.dto.FlowDtos.HistoryPointDto;
import br.com.joshua.apiflowservice.flowvisualization.adapter.in.web.dto.FlowDtos.MetricsDto;
import br.com.joshua.apiflowservice.flowvisualization.adapter.in.web.dto.FlowDtos.PageDto;
import br.com.joshua.apiflowservice.flowvisualization.adapter.in.web.dto.FlowDtos.SpanDetailDto;
import br.com.joshua.apiflowservice.flowvisualization.adapter.in.web.dto.FlowDtos.SpanDto;
import br.com.joshua.apiflowservice.flowvisualization.adapter.in.web.dto.FlowDtos.TraceDetailDto;
import br.com.joshua.apiflowservice.flowvisualization.adapter.in.web.dto.FlowDtos.TraceSummaryDto;
import br.com.joshua.apiflowservice.flowvisualization.application.TraceQueryService;
import br.com.joshua.apiflowservice.flowvisualization.application.TraceQueryService.TraceView;
import br.com.joshua.apiflowservice.flowvisualization.application.model.PageResult;
import br.com.joshua.apiflowservice.flowvisualization.application.model.TraceFilter;
import br.com.joshua.apiflowservice.flowvisualization.application.model.TraceMetrics;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** REST API consumed by the dashboard. Exposes DTOs only. */
@Validated
@RestController
@RequestMapping("/api/flow")
public class FlowTraceController {

    private final TraceQueryService queries;
    private final FlowDtoMapper mapper;

    public FlowTraceController(TraceQueryService queries, FlowDtoMapper mapper) {
        this.queries = queries;
        this.mapper = mapper;
    }

    /** Paginated, filterable list of stored traces (newest first). */
    @GetMapping("/traces")
    public PageDto<TraceSummaryDto> listTraces(
            @RequestParam(required = false) TraceStatus status,
            @RequestParam(required = false) String method,
            @RequestParam(required = false) String endpoint,
            @RequestParam(required = false) String scenario,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) @Min(0) Long minDurationMs,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        TraceFilter filter = new TraceFilter(status, blankToNull(method), blankToNull(endpoint),
                blankToNull(scenario), from, to, minDurationMs);
        PageResult<TraceSummaryDto> result = queries.search(filter, page, size).map(mapper::summary);
        return new PageDto<>(result.content(), result.page(), result.size(), result.totalElements(),
                result.totalPages());
    }

    /** Traces currently running or waiting to be persisted. */
    @GetMapping("/traces/live")
    public List<TraceSummaryDto> liveTraces() {
        return queries.liveTraces().stream().map(mapper::summary).toList();
    }

    /** Consolidated state of a trace – live or persisted – with all its spans. */
    @GetMapping("/traces/{traceId}")
    public ResponseEntity<TraceDetailDto> getTrace(@PathVariable String traceId) {
        return ResponseEntity.of(queries.findTrace(traceId).map(this::detail));
    }

    @GetMapping("/traces/{traceId}/spans")
    public ResponseEntity<List<SpanDto>> getSpans(@PathVariable String traceId) {
        return ResponseEntity.of(queries.findTrace(traceId)
                .map(view -> view.trace().spans().stream().map(mapper::span).toList()));
    }

    /** One span with its ancestors (root first), its direct children and a comparison with normal runs. */
    @GetMapping("/traces/{traceId}/spans/{spanId}")
    public ResponseEntity<SpanDetailDto> getSpan(@PathVariable String traceId, @PathVariable String spanId) {
        return ResponseEntity.of(queries.findTrace(traceId).flatMap(view -> spanDetail(view.trace(), spanId)));
    }

    /** Steps of the latest successful execution of an endpoint – used to show steps not executed. */
    @GetMapping("/blueprint")
    public ResponseEntity<BlueprintDto> blueprint(@RequestParam String method, @RequestParam String endpoint) {
        return ResponseEntity.of(queries.blueprint(method.toUpperCase(), endpoint)
                .map(b -> new BlueprintDto(b.referenceTraceId(), b.trace().httpMethod(), b.trace().endpoint(),
                        b.trace().spans().stream().map(mapper::span).toList())));
    }

    /** Latest executions in chronological order, for timelines and sparklines. */
    @GetMapping("/history")
    public List<HistoryPointDto> history(@RequestParam(defaultValue = "60") @Min(1) @Max(500) int limit) {
        return queries.history(limit).stream().map(mapper::historyPoint).toList();
    }

    @GetMapping("/metrics")
    public MetricsDto metrics() {
        TraceMetrics m = queries.metrics();
        return new MetricsDto(m.total(), m.successful(), m.failed(), FlowDtoMapper.ms(m.avgDurationMicros()),
                FlowDtoMapper.ms((double) m.maxDurationMicros()), FlowDtoMapper.ms(m.p95DurationMicros()),
                m.totalSpans(), queries.inFlightCount(), mapper.slowThresholdMs());
    }

    @GetMapping("/metrics/components")
    public List<ComponentMetricsDto> componentMetrics() {
        return queries.componentStats().stream()
                .map(s -> new ComponentMetricsDto(s.component(), s.operation(), s.type().name(), s.count(),
                        s.errors(), FlowDtoMapper.ms(s.avgDurationMicros()),
                        FlowDtoMapper.ms(s.medianDurationMicros()), FlowDtoMapper.ms((double) s.maxDurationMicros())))
                .toList();
    }

    /** Removes all stored demo traces. */
    @DeleteMapping("/traces")
    public ClearResultDto clear() {
        return new ClearResultDto(queries.clear());
    }

    private TraceDetailDto detail(TraceView view) {
        return new TraceDetailDto(mapper.summary(view.trace()),
                view.trace().spans().stream().map(mapper::span).toList(), view.live(), mapper.slowThresholdMs());
    }

    private Optional<SpanDetailDto> spanDetail(FlowTrace trace, String spanId) {
        Map<String, FlowSpan> byId = trace.spans().stream()
                .collect(Collectors.toMap(FlowSpan::spanId, Function.identity(), (a, b) -> a));
        FlowSpan span = byId.get(spanId);
        if (span == null) {
            return Optional.empty();
        }
        List<SpanDto> ancestors = new ArrayList<>();
        FlowSpan parent = byId.get(span.parentSpanId());
        while (parent != null && ancestors.size() < byId.size()) {
            ancestors.addFirst(mapper.span(parent));
            parent = byId.get(parent.parentSpanId());
        }
        List<SpanDto> children = trace.spans().stream()
                .filter(s -> spanId.equals(s.parentSpanId()))
                .map(mapper::span)
                .toList();
        Double baseline = queries.baselineMedianMicros(span.component(), span.operation())
                .map(FlowDtoMapper::ms).orElse(null);
        Double duration = FlowDtoMapper.ms(span.durationMicros());
        Double delta = baseline != null && duration != null ? Math.round((duration - baseline) * 1000) / 1000.0 : null;
        return Optional.of(new SpanDetailDto(mapper.span(span), ancestors, children, baseline, delta));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
