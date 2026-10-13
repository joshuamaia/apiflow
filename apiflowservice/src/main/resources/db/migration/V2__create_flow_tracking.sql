-- Flow tracking storage. Only metadata is stored: no request/response payloads, headers or parameters.

CREATE TABLE trace_execution
(
    id            BIGSERIAL PRIMARY KEY,
    trace_id      VARCHAR(64)  NOT NULL UNIQUE,
    http_method   VARCHAR(10)  NOT NULL,
    endpoint      VARCHAR(255) NOT NULL,
    http_status   INTEGER,
    scenario      VARCHAR(40),
    started_at    TIMESTAMPTZ  NOT NULL,
    finished_at   TIMESTAMPTZ,
    duration_us   BIGINT,
    status        VARCHAR(20)  NOT NULL,
    span_count    INTEGER      NOT NULL DEFAULT 0,
    error_message VARCHAR(500),
    truncated     BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_trace_execution_started_at ON trace_execution (started_at DESC);
CREATE INDEX idx_trace_execution_endpoint ON trace_execution (http_method, endpoint, status, started_at DESC);

CREATE TABLE trace_span
(
    id             BIGSERIAL PRIMARY KEY,
    trace_id       VARCHAR(64)  NOT NULL REFERENCES trace_execution (trace_id) ON DELETE CASCADE,
    span_id        VARCHAR(32)  NOT NULL,
    parent_span_id VARCHAR(32),
    component      VARCHAR(120) NOT NULL,
    operation      VARCHAR(160) NOT NULL,
    component_type VARCHAR(20)  NOT NULL,
    sequence       INTEGER      NOT NULL,
    started_at     TIMESTAMPTZ  NOT NULL,
    finished_at    TIMESTAMPTZ,
    duration_us    BIGINT,
    status         VARCHAR(20)  NOT NULL,
    error_type     VARCHAR(255),
    error_message  VARCHAR(500),
    tags           TEXT
);

CREATE INDEX idx_trace_span_trace ON trace_span (trace_id, sequence);
CREATE INDEX idx_trace_span_component ON trace_span (component, operation);
