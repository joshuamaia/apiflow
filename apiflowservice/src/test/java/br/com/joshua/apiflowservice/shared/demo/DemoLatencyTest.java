package br.com.joshua.apiflowservice.shared.demo;

import br.com.joshua.apiflowservice.flowtracking.api.SpanStartHook.StartedStep;
import br.com.joshua.apiflowservice.flowtracking.domain.ComponentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DemoLatencyTest {

    private final DemoLatency latency = new DemoLatency();

    private static final class Step implements StartedStep {
        final String component;
        final String operation;
        final Map<String, String> tags = new HashMap<>();

        Step(String component, String operation) {
            this.component = component;
            this.operation = operation;
        }

        @Override
        public String component() {
            return component;
        }

        @Override
        public String operation() {
            return operation;
        }

        @Override
        public ComponentType type() {
            return ComponentType.SERVICE;
        }

        @Override
        public void tag(String key, String value) {
            tags.put(key, value);
        }
    }

    @AfterEach
    void clear() {
        DemoLatency.clear();
    }

    @Test
    void matchesComponentOrComponentDotOperationIgnoringCase() {
        Step step = new Step("ProductRepository", "findById");
        assertThat(DemoLatency.matches("productrepository", step)).isTrue();
        assertThat(DemoLatency.matches("ProductRepository.findById", step)).isTrue();
        assertThat(DemoLatency.matches("ProductRepository.save", step)).isFalse();
        assertThat(DemoLatency.matches("ProductService", step)).isFalse();
    }

    @Test
    void delaysOnlyTheFirstMatchingStepAndTagsIt() {
        DemoLatency.request(20, "ProductService");
        Step other = new Step("OrderController", "createOrder");
        Step first = new Step("ProductService", "getProduct");
        Step second = new Step("ProductService", "getProduct");

        latency.onSpanStarted(other);
        long start = System.nanoTime();
        latency.onSpanStarted(first);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        latency.onSpanStarted(second);

        assertThat(other.tags).isEmpty();
        assertThat(first.tags).containsEntry(DemoLatency.TAG, "20");
        assertThat(elapsedMs).isGreaterThanOrEqualTo(19);
        assertThat(second.tags).isEmpty();
    }

    @Test
    void defaultsToStockServiceWhenNoTargetIsGiven() {
        DemoLatency.request(15, null);
        Step service = new Step("ProductService", "getProduct");
        Step stock = new Step("StockService", "reserveStock");

        latency.onSpanStarted(service);
        latency.onSpanStarted(stock);

        assertThat(service.tags).isEmpty();
        assertThat(stock.tags).containsEntry(DemoLatency.TAG, "15");
    }

    @Test
    void noRequestMeansNoDelay() {
        Step step = new Step("StockService", "reserveStock");
        latency.onSpanStarted(step);
        assertThat(step.tags).isEmpty();
    }
}
