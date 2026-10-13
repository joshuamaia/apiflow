package br.com.joshua.apiflowservice.flowvisualization.adapter.out.jdbc;

import br.com.joshua.apiflowservice.flowtracking.domain.ComponentType;
import br.com.joshua.apiflowservice.flowtracking.domain.FlowSpan;
import br.com.joshua.apiflowservice.flowtracking.domain.FlowTrace;
import br.com.joshua.apiflowservice.flowtracking.domain.SpanStatus;
import br.com.joshua.apiflowservice.flowtracking.domain.SpanTags;
import br.com.joshua.apiflowservice.flowtracking.domain.TraceStatus;
import br.com.joshua.apiflowservice.flowvisualization.application.model.ComponentStats;
import br.com.joshua.apiflowservice.flowvisualization.application.model.PageResult;
import br.com.joshua.apiflowservice.flowvisualization.application.model.TraceFilter;
import br.com.joshua.apiflowservice.flowvisualization.application.model.TraceMetrics;
import br.com.joshua.apiflowservice.flowvisualization.application.port.out.TraceReadModel;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Read model implemented with plain SQL over {@code trace_execution} / {@code trace_span}. It depends
 * only on the schema owned by the tracking module, not on its JPA entities.
 */
@Repository
class JdbcTraceReadModel implements TraceReadModel {

    private static final String TRACE_COLUMNS = """
            trace_id, http_method, endpoint, http_status, scenario, started_at, finished_at, duration_us,
            status, span_count, error_message, truncated""";

    private final JdbcClient jdbc;

    JdbcTraceReadModel(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PageResult<FlowTrace> search(TraceFilter filter, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        Map<String, Object> params = new LinkedHashMap<>();
        if (filter.status() != null) {
            where.append(" AND status = :status");
            params.put("status", filter.status().name());
        }
        if (filter.httpMethod() != null) {
            where.append(" AND http_method = :method");
            params.put("method", filter.httpMethod().toUpperCase());
        }
        if (filter.endpoint() != null) {
            where.append(" AND endpoint ILIKE :endpoint");
            params.put("endpoint", "%" + filter.endpoint() + "%");
        }
        if (filter.scenario() != null) {
            where.append(" AND scenario = :scenario");
            params.put("scenario", filter.scenario());
        }
        if (filter.from() != null) {
            where.append(" AND started_at >= :from");
            params.put("from", Timestamp.from(filter.from()));
        }
        if (filter.to() != null) {
            where.append(" AND started_at <= :to");
            params.put("to", Timestamp.from(filter.to()));
        }
        if (filter.minDurationMs() != null) {
            where.append(" AND duration_us >= :minDuration");
            params.put("minDuration", filter.minDurationMs() * 1000);
        }

        long total = jdbc.sql("SELECT count(*) FROM trace_execution" + where).params(params)
                .query(Long.class).single();
        List<FlowTrace> content = jdbc.sql("SELECT " + TRACE_COLUMNS + " FROM trace_execution" + where
                        + " ORDER BY started_at DESC LIMIT :limit OFFSET :offset")
                .params(params)
                .param("limit", size)
                .param("offset", (long) page * size)
                .query((rs, n) -> mapTrace(rs, List.of()))
                .list();
        return new PageResult<>(content, page, size, total);
    }

    @Override
    public Optional<FlowTrace> findTrace(String traceId) {
        List<FlowSpan> spans = jdbc.sql("""
                        SELECT trace_id, span_id, parent_span_id, component, operation, component_type, sequence,
                               started_at, finished_at, duration_us, status, error_type, error_message, tags
                          FROM trace_span WHERE trace_id = :traceId ORDER BY sequence""")
                .param("traceId", traceId)
                .query((rs, n) -> mapSpan(rs))
                .list();
        return jdbc.sql("SELECT " + TRACE_COLUMNS + " FROM trace_execution WHERE trace_id = :traceId")
                .param("traceId", traceId)
                .query((rs, n) -> mapTrace(rs, spans))
                .optional();
    }

    @Override
    public Optional<String> latestSuccessfulTraceId(String httpMethod, String endpoint) {
        return jdbc.sql("""
                        SELECT trace_id FROM trace_execution
                         WHERE http_method = :method AND endpoint = :endpoint AND status = 'SUCCESS' AND span_count > 0
                         ORDER BY started_at DESC LIMIT 1""")
                .param("method", httpMethod)
                .param("endpoint", endpoint)
                .query(String.class)
                .optional();
    }

    @Override
    public TraceMetrics metrics() {
        return jdbc.sql("""
                        SELECT count(*)                                                     AS total,
                               count(*) FILTER (WHERE status = 'SUCCESS')                   AS successful,
                               count(*) FILTER (WHERE status = 'ERROR')                     AS failed,
                               coalesce(avg(duration_us), 0)                                AS avg_us,
                               coalesce(max(duration_us), 0)                                AS max_us,
                               coalesce(percentile_cont(0.95) WITHIN GROUP (ORDER BY duration_us), 0) AS p95_us,
                               coalesce(sum(span_count), 0)                                 AS spans
                          FROM trace_execution""")
                .query((rs, n) -> new TraceMetrics(rs.getLong("total"), rs.getLong("successful"),
                        rs.getLong("failed"), rs.getDouble("avg_us"), rs.getLong("max_us"), rs.getDouble("p95_us"),
                        rs.getLong("spans")))
                .single();
    }

    @Override
    public List<ComponentStats> componentStats() {
        return jdbc.sql("""
                        SELECT component, operation, component_type,
                               count(*)                                                  AS executions,
                               count(*) FILTER (WHERE status = 'ERROR')                  AS errors,
                               avg(duration_us)                                          AS avg_us,
                               percentile_cont(0.5) WITHIN GROUP (ORDER BY duration_us)  AS p50_us,
                               max(duration_us)                                          AS max_us
                          FROM trace_span
                         WHERE duration_us IS NOT NULL
                         GROUP BY component, operation, component_type
                         ORDER BY avg_us DESC""")
                .query((rs, n) -> new ComponentStats(rs.getString("component"), rs.getString("operation"),
                        ComponentType.valueOf(rs.getString("component_type")), rs.getLong("executions"),
                        rs.getLong("errors"), rs.getDouble("avg_us"), rs.getDouble("p50_us"), rs.getLong("max_us")))
                .list();
    }

    @Override
    public Optional<Double> baselineMedianMicros(String component, String operation) {
        return jdbc.sql("""
                        SELECT percentile_cont(0.5) WITHIN GROUP (ORDER BY duration_us)
                          FROM trace_span
                         WHERE component = :component AND operation = :operation AND status = 'COMPLETED'
                           AND duration_us IS NOT NULL
                           AND (tags IS NULL OR tags NOT LIKE '%demo.artificialDelayMs=%')""")
                .param("component", component)
                .param("operation", operation)
                .query((rs, n) -> {
                    double value = rs.getDouble(1);
                    return rs.wasNull() ? null : value;
                })
                .optional();
    }

    private static FlowTrace mapTrace(ResultSet rs, List<FlowSpan> spans) throws SQLException {
        return new FlowTrace(rs.getString("trace_id"), rs.getString("http_method"), rs.getString("endpoint"),
                (Integer) rs.getObject("http_status"), rs.getString("scenario"), instant(rs, "started_at"),
                instant(rs, "finished_at"), (Long) rs.getObject("duration_us"),
                TraceStatus.valueOf(rs.getString("status")), rs.getString("error_message"), rs.getInt("span_count"),
                new ArrayList<>(spans), rs.getBoolean("truncated"));
    }

    private static FlowSpan mapSpan(ResultSet rs) throws SQLException {
        return new FlowSpan(rs.getString("trace_id"), rs.getString("span_id"), rs.getString("parent_span_id"),
                rs.getString("component"), rs.getString("operation"),
                ComponentType.valueOf(rs.getString("component_type")), rs.getInt("sequence"),
                instant(rs, "started_at"), instant(rs, "finished_at"), (Long) rs.getObject("duration_us"),
                SpanStatus.valueOf(rs.getString("status")), rs.getString("error_type"),
                rs.getString("error_message"), SpanTags.decode(rs.getString("tags")));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }
}
