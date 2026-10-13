package br.com.joshua.apiflowservice.flowtracking.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "trace_span")
class TraceSpanEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trace_id", nullable = false, length = 64)
    private String traceId;

    @Column(name = "span_id", nullable = false, length = 32)
    private String spanId;

    @Column(name = "parent_span_id", length = 32)
    private String parentSpanId;

    @Column(name = "component", nullable = false, length = 120)
    private String component;

    @Column(name = "operation", nullable = false, length = 160)
    private String operation;

    @Column(name = "component_type", nullable = false, length = 20)
    private String componentType;

    @Column(name = "sequence", nullable = false)
    private int sequence;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "duration_us")
    private Long durationMicros;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "error_type")
    private String errorType;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "tags", columnDefinition = "text")
    private String tags;

    protected TraceSpanEntity() {
    }

    TraceSpanEntity(String traceId, String spanId, String parentSpanId, String component, String operation,
                    String componentType, int sequence, Instant startedAt, Instant finishedAt, Long durationMicros,
                    String status, String errorType, String errorMessage, String tags) {
        this.traceId = traceId;
        this.spanId = spanId;
        this.parentSpanId = parentSpanId;
        this.component = component;
        this.operation = operation;
        this.componentType = componentType;
        this.sequence = sequence;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.durationMicros = durationMicros;
        this.status = status;
        this.errorType = errorType;
        this.errorMessage = errorMessage;
        this.tags = tags;
    }
}
