package br.com.joshua.apiflowservice.flowtracking.domain;

public enum TraceStatus {
    RUNNING,
    SUCCESS,
    ERROR;

    /** HTTP 4xx/5xx responses are reported as {@link #ERROR}, everything else as {@link #SUCCESS}. */
    public static TraceStatus fromHttpStatus(int httpStatus) {
        return httpStatus >= 400 ? ERROR : SUCCESS;
    }
}
