package br.com.joshua.apiflowservice.flowtracking.api;

/**
 * Handle of an open span. Must be closed exactly once, typically with try/finally.
 */
public interface FlowScope extends AutoCloseable {

    /** Scope returned when there is no tracked request on the current thread. */
    FlowScope NOOP = new FlowScope() {
        @Override
        public void markError(Throwable error) {
        }

        @Override
        public void close() {
        }
    };

    /** Records that the operation failed. The exception is not swallowed. */
    void markError(Throwable error);

    /** Finishes the span, computing its duration. Never throws. */
    @Override
    void close();
}
