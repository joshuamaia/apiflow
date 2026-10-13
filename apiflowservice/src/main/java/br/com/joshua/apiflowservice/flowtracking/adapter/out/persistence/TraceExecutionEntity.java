package br.com.joshua.apiflowservice.flowtracking.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "trace_execution")
class TraceExecutionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trace_id", nullable = false, unique = true, length = 64)
    private String traceId;

    @Column(name = "http_method", nullable = false, length = 10)
    private String httpMethod;

    @Column(name = "endpoint", nullable = false)
    private String endpoint;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "scenario", length = 40)
    private String scenario;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "duration_us")
    private Long durationMicros;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "span_count", nullable = false)
    private int spanCount;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "truncated", nullable = false)
    private boolean truncated;

    protected TraceExecutionEntity() {
    }

    TraceExecutionEntity(String traceId, String httpMethod, String endpoint, Integer httpStatus, String scenario,
                         Instant startedAt, Instant finishedAt, Long durationMicros, String status, int spanCount,
                         String errorMessage, boolean truncated) {
        this.traceId = traceId;
        this.httpMethod = httpMethod;
        this.endpoint = endpoint;
        this.httpStatus = httpStatus;
        this.scenario = scenario;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.durationMicros = durationMicros;
        this.status = status;
        this.spanCount = spanCount;
        this.errorMessage = errorMessage;
        this.truncated = truncated;
    }

    Long getId() {
        return id;
    }
}
