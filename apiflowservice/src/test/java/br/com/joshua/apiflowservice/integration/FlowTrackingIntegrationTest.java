package br.com.joshua.apiflowservice.integration;

import br.com.joshua.apiflowservice.flowtracking.api.FlowEventListener;
import br.com.joshua.apiflowservice.flowtracking.domain.ComponentType;
import br.com.joshua.apiflowservice.flowtracking.domain.FlowEvent;
import br.com.joshua.apiflowservice.flowtracking.domain.FlowEventType;
import br.com.joshua.apiflowservice.flowtracking.domain.FlowSpan;
import br.com.joshua.apiflowservice.flowtracking.domain.FlowTrace;
import br.com.joshua.apiflowservice.flowtracking.domain.SpanStatus;
import br.com.joshua.apiflowservice.flowtracking.domain.TraceStatus;
import br.com.joshua.apiflowservice.flowvisualization.application.TraceQueryService;
import br.com.joshua.apiflowservice.flowvisualization.application.TraceQueryService.TraceView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.stream.Collectors;

import static br.com.joshua.apiflowservice.flowtracking.application.RecordingSupport.await;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end check of the tracking pipeline against a real PostgreSQL: HTTP request → filter → aspect →
 * Micrometer spans → async persistence → visualization API.
 */
@Testcontainers
@AutoConfigureTracing
@AutoConfigureMockMvc
@SpringBootTest(properties = "flow.tracking.retention.cleanup-interval=1h")
class FlowTrackingIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    @TestConfiguration
    static class EventCapture {
        @Bean
        CapturingListener capturingListener() {
            return new CapturingListener();
        }
    }

    static class CapturingListener implements FlowEventListener {
        final List<FlowEvent> events = new CopyOnWriteArrayList<>();

        @Override
        public void onEvent(FlowEvent event) {
            events.add(event);
        }
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private TraceQueryService queries;
    @Autowired
    private CapturingListener listener;
    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void cleanTraces() {
        queries.clear();
        listener.events.clear();
    }

    @Test
    void successfulOrderProducesACorrelatedHierarchyOfRealSpans() throws Exception {
        String traceId = postOrder("""
                {"items":[{"productId":1,"quantity":1},{"productId":2,"quantity":2}]}""", 201);

        FlowTrace trace = awaitPersisted(traceId);
        assertThat(trace.status()).isEqualTo(TraceStatus.SUCCESS);
        assertThat(trace.httpStatus()).isEqualTo(201);
        assertThat(trace.endpoint()).isEqualTo("/api/orders");
        assertThat(trace.durationMicros()).isPositive();
        assertThat(trace.spans()).extracting(FlowSpan::traceId).containsOnly(traceId);
        assertThat(trace.spans()).allSatisfy(span -> {
            assertThat(span.status()).isEqualTo(SpanStatus.COMPLETED);
            assertThat(span.durationMicros()).isNotNull().isLessThanOrEqualTo(trace.durationMicros());
            assertThat(span.startedAt()).isAfterOrEqualTo(trace.startedAt());
        });

        Map<String, FlowSpan> byId = trace.spans().stream()
                .collect(Collectors.toMap(FlowSpan::spanId, Function.identity()));
        FlowSpan controller = trace.spans().getFirst();
        assertThat(controller.component()).isEqualTo("OrderController");
        assertThat(controller.type()).isEqualTo(ComponentType.CONTROLLER);
        assertThat(byId).doesNotContainKey(controller.parentSpanId()); // parent is the HTTP server span

        FlowSpan useCase = single(trace, "CreateOrderUseCase", "execute");
        assertThat(useCase.parentSpanId()).isEqualTo(controller.spanId());
        for (FlowSpan span : trace.spans()) {
            if (span != controller) {
                assertThat(byId).as("parent of %s", span.component()).containsKey(span.parentSpanId());
            }
        }
        assertThat(spans(trace, "ProductService", "getProduct")).hasSize(2)
                .allSatisfy(s -> assertThat(s.parentSpanId()).isEqualTo(useCase.spanId()));
        assertThat(spans(trace, "StockService", "reserveStock")).hasSize(2);

        FlowSpan orderRepository = single(trace, "OrderRepository", "save");
        assertThat(orderRepository.parentSpanId()).isEqualTo(useCase.spanId());
        FlowSpan insert = single(trace, "PostgreSQL", "INSERT orders, order_items");
        assertThat(insert.type()).isEqualTo(ComponentType.DATABASE);
        assertThat(insert.parentSpanId()).isEqualTo(orderRepository.spanId());
        assertThat(trace.spans()).filteredOn(s -> s.type() == ComponentType.DATABASE).hasSize(5);
    }

    @Test
    void insufficientStockMarksTheFailingStepAndSkipsPersistence() throws Exception {
        int stockBefore = stockOf(1L);
        String traceId = postOrder("""
                {"items":[{"productId":1,"quantity":1},{"productId":6,"quantity":999}]}""", 422);

        FlowTrace trace = awaitPersisted(traceId);
        assertThat(trace.status()).isEqualTo(TraceStatus.ERROR);
        assertThat(trace.httpStatus()).isEqualTo(422);
        assertThat(trace.errorMessage()).contains("Insufficient stock for product 6");

        List<FlowSpan> stock = spans(trace, "StockService", "reserveStock");
        assertThat(stock).extracting(FlowSpan::status).containsExactly(SpanStatus.COMPLETED, SpanStatus.ERROR);
        assertThat(stock.get(1).errorType()).endsWith("InsufficientStockException");
        assertThat(single(trace, "OrderController", "createOrder").status()).isEqualTo(SpanStatus.ERROR);
        assertThat(spans(trace, "OrderRepository", "save")).isEmpty();
        assertThat(stockOf(1L)).as("reservation of the first line was rolled back").isEqualTo(stockBefore);
    }

    @Test
    void artificialDelayIsAttributedToStockServiceAndTagged() throws Exception {
        MvcResult result = mvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Flow-Scenario", "slow")
                        .header("X-Demo-Delay-Ms", "300")
                        .content("""
                                {"items":[{"productId":1,"quantity":1}]}"""))
                .andExpect(status().isCreated())
                .andReturn();

        FlowTrace trace = awaitPersisted(result.getResponse().getHeader("X-Flow-Trace-Id"));
        FlowSpan stock = single(trace, "StockService", "reserveStock");
        assertThat(trace.scenario()).isEqualTo("slow");
        assertThat(stock.durationMicros()).isGreaterThanOrEqualTo(300_000);
        assertThat(stock.tags()).containsEntry("demo.artificialDelayMs", "300");
        assertThat(single(trace, "ProductService", "getProduct").durationMicros()).isLessThan(300_000);
    }

    @Test
    void propagatesAnIncomingW3cTraceId() throws Exception {
        String incoming = "4bf92f3577b34da6a3ce929d0e0e4736";
        mvc.perform(get("/api/products/1").header("traceparent", "00-" + incoming + "-00f067aa0ba902b7-01"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Flow-Trace-Id", incoming));

        FlowTrace trace = awaitPersisted(incoming);
        assertThat(trace.endpoint()).isEqualTo("/api/products/{id}");
        assertThat(trace.spans()).extracting(FlowSpan::traceId).containsOnly(incoming);
    }

    @Test
    void publishesLifecycleEventsForEachTraceWithoutMixingThem() throws Exception {
        String first = postOrder("""
                {"items":[{"productId":2,"quantity":1}]}""", 201);
        String second = postOrder("""
                {"items":[{"productId":6,"quantity":999}]}""", 422);
        awaitPersisted(first);
        awaitPersisted(second);
        await(() -> eventTypes(second).contains(FlowEventType.TRACE_PERSISTED));

        assertThat(eventTypes(first).getFirst()).isEqualTo(FlowEventType.TRACE_STARTED);
        assertThat(eventTypes(first)).doesNotContain(FlowEventType.SPAN_FAILED)
                .endsWith(FlowEventType.TRACE_COMPLETED, FlowEventType.TRACE_PERSISTED);
        assertThat(eventTypes(second)).contains(FlowEventType.SPAN_FAILED)
                .endsWith(FlowEventType.TRACE_COMPLETED, FlowEventType.TRACE_PERSISTED);
        long started = listener.events.stream()
                .filter(e -> e.traceId().equals(first) && e.type() == FlowEventType.SPAN_STARTED).count();
        long finished = listener.events.stream()
                .filter(e -> e.traceId().equals(first) && e.type() == FlowEventType.SPAN_COMPLETED).count();
        assertThat(started).isEqualTo(finished).isEqualTo(awaitPersisted(first).spanCount());
    }

    @Test
    void historyListAndMetricsReflectStoredTraces() throws Exception {
        String ok = postOrder("""
                {"items":[{"productId":1,"quantity":1}]}""", 201);
        String failed = postOrder("""
                {"items":[{"productId":6,"quantity":999}]}""", 422);
        awaitPersisted(ok);
        awaitPersisted(failed);

        mvc.perform(get("/api/flow/traces").param("status", "ERROR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].traceId").value(failed))
                .andExpect(jsonPath("$.content[0].httpStatus").value(422));
        mvc.perform(get("/api/flow/traces").param("size", "1").param("page", "1"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(1));
        mvc.perform(get("/api/flow/history"))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].traceId").value(ok))
                .andExpect(jsonPath("$[1].traceId").value(failed));
        mvc.perform(get("/api/flow/metrics"))
                .andExpect(jsonPath("$.totalRequests").value(2))
                .andExpect(jsonPath("$.successfulRequests").value(1))
                .andExpect(jsonPath("$.failedRequests").value(1));
        mvc.perform(get("/api/flow/traces/{id}", ok))
                .andExpect(jsonPath("$.trace.status").value("SUCCESS"))
                .andExpect(jsonPath("$.live").value(false))
                .andExpect(jsonPath("$.spans[0].component").value("OrderController"));
        mvc.perform(get("/api/flow/blueprint").param("method", "POST").param("endpoint", "/api/orders"))
                .andExpect(jsonPath("$.referenceTraceId").value(ok));

        mvc.perform(delete("/api/flow/traces")).andExpect(jsonPath("$.deletedTraces").value(2));
        mvc.perform(get("/api/flow/traces/{id}", ok)).andExpect(status().isNotFound());
    }

    @Test
    void visualizationEndpointsAreNotTracked() throws Exception {
        mvc.perform(get("/api/flow/metrics")).andExpect(status().isOk())
                .andExpect(header().doesNotExist("X-Flow-Trace-Id"));
    }

    private String postOrder(String body, int expectedStatus) throws Exception {
        MvcResult result = mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expectedStatus))
                .andExpect(header().exists("X-Flow-Trace-Id"))
                .andReturn();
        return result.getResponse().getHeader("X-Flow-Trace-Id");
    }

    private FlowTrace awaitPersisted(String traceId) {
        await(() -> queries.findTrace(traceId).filter(view -> !view.live()).isPresent());
        return queries.findTrace(traceId).map(TraceView::trace).orElseThrow();
    }

    private List<FlowEventType> eventTypes(String traceId) {
        return listener.events.stream().filter(e -> e.traceId().equals(traceId)).map(FlowEvent::type).toList();
    }

    private int stockOf(Long productId) {
        return jdbc.sql("SELECT stock FROM products WHERE id = ?").param(productId).query(Integer.class).single();
    }

    private static List<FlowSpan> spans(FlowTrace trace, String component, String operation) {
        return trace.spans().stream()
                .filter(s -> s.component().equals(component) && s.operation().equals(operation))
                .toList();
    }

    private static FlowSpan single(FlowTrace trace, String component, String operation) {
        List<FlowSpan> found = spans(trace, component, operation);
        assertThat(found).as("%s.%s", component, operation).hasSize(1);
        return Optional.of(found.getFirst()).orElseThrow();
    }
}
