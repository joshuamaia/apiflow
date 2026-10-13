package br.com.joshua.apiflowservice.flowtracking.domain;

public enum FlowEventType {
    /** A tracked HTTP request entered the application. */
    TRACE_STARTED,
    /** An instrumented step started executing. */
    SPAN_STARTED,
    /** An instrumented step finished without throwing. */
    SPAN_COMPLETED,
    /** An instrumented step finished by throwing an exception. */
    SPAN_FAILED,
    /** The HTTP response was produced; the trace carries the final status. */
    TRACE_COMPLETED,
    /** The trace was written to durable storage and is now queryable. */
    TRACE_PERSISTED
}
