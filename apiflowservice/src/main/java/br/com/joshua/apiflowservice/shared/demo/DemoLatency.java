package br.com.joshua.apiflowservice.shared.demo;

import br.com.joshua.apiflowservice.flowtracking.api.FlowTracker;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Artificial latency used <strong>only</strong> by the "slow operation" demo scenario. The caller asks
 * for it with the {@value DemoLatencyFilter#DELAY_HEADER} header; the delay is applied once per request
 * at the point where business code calls {@link #applyIfRequested()}, and the current span is tagged
 * with {@value #TAG} so the dashboard can tell artificial latency apart from real latency.
 */
@Component
public class DemoLatency {

    public static final String TAG = "demo.artificialDelayMs";
    static final long MAX_DELAY_MS = 5_000;

    private static final ThreadLocal<Long> REQUESTED = new ThreadLocal<>();

    private final FlowTracker tracker;

    public DemoLatency(FlowTracker tracker) {
        this.tracker = tracker;
    }

    static void request(long delayMs) {
        REQUESTED.set(Math.clamp(delayMs, 0, MAX_DELAY_MS));
    }

    static void clear() {
        REQUESTED.remove();
    }

    /** Sleeps for the requested demo delay, if any. Consumes the request so it applies only once. */
    public void applyIfRequested() {
        Long delay = REQUESTED.get();
        if (delay == null || delay <= 0) {
            return;
        }
        REQUESTED.remove();
        tracker.tag(TAG, String.valueOf(delay));
        try {
            Thread.sleep(Duration.ofMillis(delay));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
