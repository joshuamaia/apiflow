package br.com.joshua.apiflowservice.flowvisualization.adapter.in.web;

import br.com.joshua.apiflowservice.flowtracking.domain.FlowEvent;
import br.com.joshua.apiflowservice.flowtracking.domain.FlowSpan;
import br.com.joshua.apiflowservice.flowtracking.domain.FlowTrace;
import br.com.joshua.apiflowservice.flowvisualization.adapter.in.web.dto.FlowDtos.FlowEventDto;
import br.com.joshua.apiflowservice.flowvisualization.adapter.in.web.dto.FlowDtos.HistoryPointDto;
import br.com.joshua.apiflowservice.flowvisualization.adapter.in.web.dto.FlowDtos.SpanDto;
import br.com.joshua.apiflowservice.flowvisualization.adapter.in.web.dto.FlowDtos.TraceSummaryDto;
import br.com.joshua.apiflowservice.flowvisualization.config.VisualizationProperties;
import org.springframework.stereotype.Component;

/** Converts tracking domain objects into API DTOs and applies the "slow" classification. */
@Component
public class FlowDtoMapper {

    static final String ARTIFICIAL_DELAY_TAG = "demo.artificialDelayMs";

    private final long slowThresholdMicros;

    public FlowDtoMapper(VisualizationProperties properties) {
        this.slowThresholdMicros = properties.getSlowThreshold().toNanos() / 1_000;
    }

    public double slowThresholdMs() {
        return slowThresholdMicros / 1000.0;
    }

    public SpanDto span(FlowSpan span) {
        boolean slow = span.durationMicros() != null && span.durationMicros() >= slowThresholdMicros;
        return new SpanDto(span.traceId(), span.spanId(), span.parentSpanId(), span.component(), span.operation(),
                span.type().name(), span.sequence(), span.startedAt(), span.finishedAt(), ms(span.durationMicros()),
                span.status().name(), slow, span.errorType(), span.errorMessage(), artificialDelay(span), span.tags());
    }

    public TraceSummaryDto summary(FlowTrace trace) {
        return new TraceSummaryDto(trace.traceId(), trace.httpMethod(), trace.endpoint(), trace.httpStatus(),
                trace.scenario(), trace.startedAt(), trace.finishedAt(), ms(trace.durationMicros()),
                trace.status().name(), trace.spanCount(), trace.errorMessage(), trace.truncated());
    }

    public HistoryPointDto historyPoint(FlowTrace trace) {
        return new HistoryPointDto(trace.traceId(), trace.startedAt(), ms(trace.durationMicros()),
                trace.status().name(), trace.httpStatus(), trace.httpMethod(), trace.endpoint(), trace.scenario());
    }

    public FlowEventDto event(FlowEvent event) {
        return new FlowEventDto(event.sequence(), event.type().name(), event.traceId(), event.timestamp(),
                event.trace() == null ? null : summary(event.trace()),
                event.span() == null ? null : span(event.span()));
    }

    public static Double ms(Long micros) {
        return micros == null ? null : micros / 1000.0;
    }

    public static double ms(double micros) {
        return Math.round(micros) / 1000.0;
    }

    private static Long artificialDelay(FlowSpan span) {
        String value = span.tags().get(ARTIFICIAL_DELAY_TAG);
        try {
            return value == null ? null : Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
