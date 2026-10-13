package br.com.joshua.apiflowservice.flowvisualization.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Settings of the visualization API ({@code flow.visualization.*}). */
@ConfigurationProperties(prefix = "flow.visualization")
public class VisualizationProperties {

    /** Spans at or above this duration are flagged as slow (yellow in the graph). */
    private Duration slowThreshold = Duration.ofMillis(250);

    /** Lifetime of an SSE connection; the browser reconnects automatically afterwards. */
    private Duration sseTimeout = Duration.ofMinutes(30);

    /** Number of recent events kept to replay to clients reconnecting with Last-Event-ID. */
    private int replayBufferSize = 1000;

    /** Maximum concurrent SSE subscribers. */
    private int maxSubscribers = 100;

    /** Capacity of the event dispatch queue; the oldest events are dropped when full. */
    private int dispatchQueueCapacity = 5000;

    public Duration getSlowThreshold() {
        return slowThreshold;
    }

    public void setSlowThreshold(Duration slowThreshold) {
        this.slowThreshold = slowThreshold;
    }

    public Duration getSseTimeout() {
        return sseTimeout;
    }

    public void setSseTimeout(Duration sseTimeout) {
        this.sseTimeout = sseTimeout;
    }

    public int getReplayBufferSize() {
        return replayBufferSize;
    }

    public void setReplayBufferSize(int replayBufferSize) {
        this.replayBufferSize = replayBufferSize;
    }

    public int getMaxSubscribers() {
        return maxSubscribers;
    }

    public void setMaxSubscribers(int maxSubscribers) {
        this.maxSubscribers = maxSubscribers;
    }

    public int getDispatchQueueCapacity() {
        return dispatchQueueCapacity;
    }

    public void setDispatchQueueCapacity(int dispatchQueueCapacity) {
        this.dispatchQueueCapacity = dispatchQueueCapacity;
    }
}
