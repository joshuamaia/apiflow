package br.com.joshua.apiflowservice.shared.demo;

import br.com.joshua.apiflowservice.flowtracking.api.SpanStartHook;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Artificial latency for demonstrations <strong>only</strong>. A request asks for it with the
 * {@value DemoLatencyFilter#DELAY_HEADER} header and chooses the step with
 * {@value DemoLatencyFilter#TARGET_HEADER} ({@code Component} or {@code Component.operation}; defaults to
 * {@value #DEFAULT_TARGET}).
 * <p>
 * The delay is applied once per request, inside the first matching tracked step, so it is measured as
 * that step's own time. The step is tagged with {@value #TAG} so the dashboard can tell artificial
 * latency apart from real latency. Business code is not aware of it.
 */
@Component
public class DemoLatency implements SpanStartHook {

    public static final String TAG = "demo.artificialDelayMs";
    public static final String DEFAULT_TARGET = "StockService";
    static final long MAX_DELAY_MS = 5_000;

    private record Request(long delayMs, String target) {
    }

    private static final ThreadLocal<Request> REQUESTED = new ThreadLocal<>();

    static void request(long delayMs, String target) {
        long delay = Math.clamp(delayMs, 0, MAX_DELAY_MS);
        if (delay > 0) {
            REQUESTED.set(new Request(delay, target == null || target.isBlank() ? DEFAULT_TARGET : target.strip()));
        }
    }

    static void clear() {
        REQUESTED.remove();
    }

    @Override
    public void onSpanStarted(StartedStep step) {
        Request request = REQUESTED.get();
        if (request == null || !matches(request.target(), step)) {
            return;
        }
        REQUESTED.remove();
        step.tag(TAG, String.valueOf(request.delayMs()));
        try {
            Thread.sleep(Duration.ofMillis(request.delayMs()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static boolean matches(String target, StartedStep step) {
        return target.equalsIgnoreCase(step.component())
                || target.equalsIgnoreCase(step.component() + "." + step.operation());
    }
}
