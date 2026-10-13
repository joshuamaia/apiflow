package br.com.joshua.apiflowservice.flowtracking.adapter.out.persistence;

import br.com.joshua.apiflowservice.flowtracking.api.TraceRepository;
import br.com.joshua.apiflowservice.flowtracking.domain.FlowSpan;
import br.com.joshua.apiflowservice.flowtracking.domain.FlowTrace;
import br.com.joshua.apiflowservice.flowtracking.domain.SpanTags;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** Stores a finished trace and its spans in a single transaction. */
@Repository
public class JpaTraceRepository implements TraceRepository {

    private final TraceExecutionJpaRepository executions;
    private final TraceSpanJpaRepository spans;

    JpaTraceRepository(TraceExecutionJpaRepository executions, TraceSpanJpaRepository spans) {
        this.executions = executions;
        this.spans = spans;
    }

    @Override
    @Transactional
    public void save(FlowTrace trace) {
        executions.save(new TraceExecutionEntity(trace.traceId(), trace.httpMethod(), trace.endpoint(),
                trace.httpStatus(), trace.scenario(), trace.startedAt(), trace.finishedAt(), trace.durationMicros(),
                trace.status().name(), trace.spanCount(), trace.errorMessage(), trace.truncated()));
        List<TraceSpanEntity> entities = trace.spans().stream().map(JpaTraceRepository::toEntity).toList();
        spans.saveAll(entities);
    }

    @Override
    @Transactional
    public int applyRetention(int maxTraces, Instant cutoff) {
        return executions.deleteBeyondRetention(maxTraces, cutoff);
    }

    @Override
    @Transactional
    public int deleteAll() {
        return executions.deleteAllTraces();
    }

    private static TraceSpanEntity toEntity(FlowSpan span) {
        return new TraceSpanEntity(span.traceId(), span.spanId(), span.parentSpanId(), span.component(),
                span.operation(), span.type().name(), span.sequence(), span.startedAt(), span.finishedAt(),
                span.durationMicros(), span.status().name(), span.errorType(), span.errorMessage(),
                SpanTags.encode(span.tags()));
    }
}
