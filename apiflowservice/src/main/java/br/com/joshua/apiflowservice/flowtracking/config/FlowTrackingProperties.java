package br.com.joshua.apiflowservice.flowtracking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Settings of the flow tracking mechanism ({@code flow.tracking.*}). */
@ConfigurationProperties(prefix = "flow.tracking")
public class FlowTrackingProperties {

    /** Master switch. When disabled no request opens a trace, so no span is recorded. */
    private boolean enabled = true;

    /** Request paths (Spring path patterns) that open a trace. */
    private List<String> includePaths = new ArrayList<>(List.of("/api/orders/**", "/api/products/**"));

    /** Request paths never traced, even if included. */
    private List<String> excludePaths = new ArrayList<>(List.of("/api/flow/**", "/actuator/**"));

    /** Maximum traces kept in memory (running or waiting for persistence). */
    private int maxInFlightTraces = 500;

    /** Maximum spans recorded per trace; extra spans are dropped and the trace is flagged as truncated. */
    private int maxSpansPerTrace = 200;

    /** Capacity of the asynchronous persistence queue. */
    private int persistenceQueueCapacity = 1000;

    private final Retention retention = new Retention();

    public static class Retention {
        /** Maximum number of stored traces. */
        private int maxTraces = 1000;
        /** Traces older than this are removed. */
        private Duration maxAge = Duration.ofHours(24);
        /** Interval between retention runs. */
        private Duration cleanupInterval = Duration.ofMinutes(1);

        public int getMaxTraces() {
            return maxTraces;
        }

        public void setMaxTraces(int maxTraces) {
            this.maxTraces = maxTraces;
        }

        public Duration getMaxAge() {
            return maxAge;
        }

        public void setMaxAge(Duration maxAge) {
            this.maxAge = maxAge;
        }

        public Duration getCleanupInterval() {
            return cleanupInterval;
        }

        public void setCleanupInterval(Duration cleanupInterval) {
            this.cleanupInterval = cleanupInterval;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getIncludePaths() {
        return includePaths;
    }

    public void setIncludePaths(List<String> includePaths) {
        this.includePaths = includePaths;
    }

    public List<String> getExcludePaths() {
        return excludePaths;
    }

    public void setExcludePaths(List<String> excludePaths) {
        this.excludePaths = excludePaths;
    }

    public int getMaxInFlightTraces() {
        return maxInFlightTraces;
    }

    public void setMaxInFlightTraces(int maxInFlightTraces) {
        this.maxInFlightTraces = maxInFlightTraces;
    }

    public int getMaxSpansPerTrace() {
        return maxSpansPerTrace;
    }

    public void setMaxSpansPerTrace(int maxSpansPerTrace) {
        this.maxSpansPerTrace = maxSpansPerTrace;
    }

    public int getPersistenceQueueCapacity() {
        return persistenceQueueCapacity;
    }

    public void setPersistenceQueueCapacity(int persistenceQueueCapacity) {
        this.persistenceQueueCapacity = persistenceQueueCapacity;
    }

    public Retention getRetention() {
        return retention;
    }
}
