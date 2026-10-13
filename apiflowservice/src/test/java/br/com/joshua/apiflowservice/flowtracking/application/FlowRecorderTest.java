package br.com.joshua.apiflowservice.flowtracking.application;

import br.com.joshua.apiflowservice.flowtracking.application.ActiveTrace.ActiveSpan;
import br.com.joshua.apiflowservice.flowtracking.application.RecordingSupport.FakeTicker;
import br.com.joshua.apiflowservice.flowtracking.application.RecordingSupport.InMemoryTraceRepository;
import br.com.joshua.apiflowservice.flowtracking.application.RecordingSupport.RecordingPublisher;
import br.com.joshua.apiflowservice.flowtracking.domain.ComponentType;
import br.com.joshua.apiflowservice.flowtracking.domain.FlowEvent;
import br.com.joshua.apiflowservice.flowtracking.domain.FlowEventType;
import br.com.joshua.apiflowservice.flowtracking.domain.FlowSpan;
import br.com.joshua.apiflowservice.flowtracking.domain.FlowTrace;
import br.com.joshua.apiflowservice.flowtracking.domain.SpanStatus;
import br.com.joshua.apiflowservice.flowtracking.domain.TraceStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static br.com.joshua.apiflowservice.flowtracking.application.RecordingSupport.T0;
import static br.com.joshua.apiflowservice.flowtracking.application.RecordingSupport.await;
import static org.assertj.core.api.Assertions.assertThat;

class FlowRecorderTest {

    private final FakeTicker ticker = new FakeTicker();
    private final RecordingPublisher publisher = new RecordingPublisher();
    private final InMemoryTraceRepository repository = new InMemoryTraceRepository();
    private AsyncTraceWriter writer;
    private FlowRecorder recorder;

    @BeforeEach
    void setUp() {
        writer = new AsyncTraceWriter(repository, 10);
        writer.start();
        recorder = new FlowRecorder(publisher, writer, RecordingSupport.fixedClock(), ticker::read, 10, 50);
    }

    @AfterEach
    void tearDown() {
        writer.stop();
    }

    @Test
    void capturesSpansWithSameTraceIdAndParentChildHierarchy() {
        ActiveTrace trace = recorder.beginTrace("trace-1", "POST", "/api/orders", null).orElseThrow();

        ActiveSpan controller = recorder.openSpan("OrderController", "createOrder", ComponentType.CONTROLLER, null, null);
        ActiveSpan useCase = recorder.openSpan("CreateOrderUseCase", "execute", ComponentType.USE_CASE, null, null);
        ActiveSpan repositorySpan = recorder.openSpan("OrderRepository", "save", ComponentType.REPOSITORY, null, null);
        recorder.closeSpan(repositorySpan, null);
        recorder.closeSpan(useCase, null);
        recorder.closeSpan(controller, null);
        recorder.endTrace(trace, 201, "/api/orders", null);

        await(() -> repository.saved.size() == 1);
        FlowTrace saved = repository.saved.getFirst();
        assertThat(saved.spans()).extracting(FlowSpan::traceId).containsOnly("trace-1");
        assertThat(saved.spans()).extracting(FlowSpan::sequence).containsExactly(0, 1, 2);

        FlowSpan root = saved.spans().get(0);
        FlowSpan child = saved.spans().get(1);
        FlowSpan grandChild = saved.spans().get(2);
        assertThat(root.parentSpanId()).isNull();
        assertThat(child.parentSpanId()).isEqualTo(root.spanId());
        assertThat(grandChild.parentSpanId()).isEqualTo(child.spanId());
        assertThat(saved.status()).isEqualTo(TraceStatus.SUCCESS);
        assertThat(saved.httpStatus()).isEqualTo(201);
    }

    @Test
    void usesIdsProvidedByTheTracingSystemWhenAvailable() {
        ActiveTrace trace = recorder.beginTrace("trace-ids", "GET", "/api/products", null).orElseThrow();

        ActiveSpan span = recorder.openSpan("ProductController", "list", ComponentType.CONTROLLER,
                "aaaaaaaaaaaaaaaa", "bbbbbbbbbbbbbbbb");
        recorder.closeSpan(span, null);
        recorder.endTrace(trace, 200, null, null);

        await(() -> repository.saved.size() == 1);
        FlowSpan saved = repository.saved.getFirst().spans().getFirst();
        assertThat(saved.spanId()).isEqualTo("aaaaaaaaaaaaaaaa");
        assertThat(saved.parentSpanId()).isEqualTo("bbbbbbbbbbbbbbbb");
    }

    @Test
    void computesDurationsFromTheMonotonicClock() {
        ActiveTrace trace = recorder.beginTrace("trace-2", "POST", "/api/orders", null).orElseThrow();
        ticker.advanceMicros(100);
        ActiveSpan outer = recorder.openSpan("OrderController", "createOrder", ComponentType.CONTROLLER, null, null);
        ticker.advanceMicros(250);
        ActiveSpan inner = recorder.openSpan("StockService", "reserveStock", ComponentType.SERVICE, null, null);
        ticker.advanceMicros(1_500);
        recorder.closeSpan(inner, null);
        ticker.advanceMicros(50);
        recorder.closeSpan(outer, null);
        ticker.advanceMicros(10);
        recorder.endTrace(trace, 200, null, null);

        await(() -> repository.saved.size() == 1);
        FlowTrace saved = repository.saved.getFirst();
        assertThat(saved.durationMicros()).isEqualTo(1_910);
        assertThat(saved.spans().get(0).durationMicros()).isEqualTo(1_800);
        assertThat(saved.spans().get(1).durationMicros()).isEqualTo(1_500);
        assertThat(saved.spans().get(0).startedAt()).isEqualTo(T0.plusNanos(100_000));
        assertThat(saved.spans().get(1).startedAt()).isEqualTo(T0.plusNanos(350_000));
        assertThat(saved.spans().get(1).finishedAt()).isEqualTo(T0.plusNanos(1_850_000));
    }

    @Test
    void identifiesErrorsAndDerivesTheTraceErrorFromTheFailingStep() {
        ActiveTrace trace = recorder.beginTrace("trace-3", "POST", "/api/orders", "stock-error").orElseThrow();
        ActiveSpan controller = recorder.openSpan("OrderController", "createOrder", ComponentType.CONTROLLER, null, null);
        ActiveSpan stock = recorder.openSpan("StockService", "reserveStock", ComponentType.SERVICE, null, null);
        IllegalStateException failure = new IllegalStateException("Insufficient stock for product 6");
        recorder.closeSpan(stock, failure);
        recorder.closeSpan(controller, failure);
        // the exception was handled by an exception handler: the filter only sees the 422 status
        recorder.endTrace(trace, 422, "/api/orders", null);

        await(() -> repository.saved.size() == 1);
        FlowTrace saved = repository.saved.getFirst();
        assertThat(saved.status()).isEqualTo(TraceStatus.ERROR);
        assertThat(saved.scenario()).isEqualTo("stock-error");
        assertThat(saved.errorMessage()).isEqualTo("Insufficient stock for product 6");
        assertThat(saved.spans()).extracting(FlowSpan::status).containsOnly(SpanStatus.ERROR);
        assertThat(saved.spans().get(1).errorType()).isEqualTo(IllegalStateException.class.getName());
    }

    @Test
    void reportsSpansLeftOpenAsIncomplete() {
        ActiveTrace trace = recorder.beginTrace("trace-open", "GET", "/api/products", null).orElseThrow();
        recorder.openSpan("ProductController", "list", ComponentType.CONTROLLER, null, null);
        recorder.endTrace(trace, 200, null, null);

        await(() -> repository.saved.size() == 1);
        FlowSpan span = repository.saved.getFirst().spans().getFirst();
        assertThat(span.status()).isEqualTo(SpanStatus.ERROR);
        assertThat(span.errorType()).isEqualTo("IncompleteSpan");
    }

    @Test
    void publishesLifecycleEventsInOrder() {
        ActiveTrace trace = recorder.beginTrace("trace-4", "POST", "/api/orders", null).orElseThrow();
        ActiveSpan ok = recorder.openSpan("A", "a", ComponentType.SERVICE, null, null);
        recorder.closeSpan(ok, null);
        ActiveSpan failed = recorder.openSpan("B", "b", ComponentType.SERVICE, null, null);
        recorder.closeSpan(failed, new RuntimeException("boom"));
        recorder.endTrace(trace, 500, null, null);

        await(() -> publisher.events.size() == 7);
        assertThat(publisher.events).extracting(FlowEvent::type).containsExactly(
                FlowEventType.TRACE_STARTED,
                FlowEventType.SPAN_STARTED, FlowEventType.SPAN_COMPLETED,
                FlowEventType.SPAN_STARTED, FlowEventType.SPAN_FAILED,
                FlowEventType.TRACE_COMPLETED, FlowEventType.TRACE_PERSISTED);
        assertThat(publisher.events).extracting(FlowEvent::traceId).containsOnly("trace-4");
        assertThat(publisher.events).extracting(FlowEvent::sequence).isSorted().doesNotHaveDuplicates();
    }

    @Test
    void keepsConcurrentTracesApart() throws Exception {
        Runnable request = () -> {
            String id = Thread.currentThread().getName();
            ActiveTrace trace = recorder.beginTrace(id, "GET", "/api/products", null).orElseThrow();
            for (int i = 0; i < 5; i++) {
                recorder.closeSpan(recorder.openSpan("C" + i, "op", ComponentType.SERVICE, null, null), null);
            }
            recorder.endTrace(trace, 200, null, null);
        };
        CompletableFuture<Void> a = CompletableFuture.runAsync(() -> runNamed("trace-a", request));
        CompletableFuture<Void> b = CompletableFuture.runAsync(() -> runNamed("trace-b", request));
        CompletableFuture.allOf(a, b).get();

        await(() -> repository.saved.size() == 2);
        for (FlowTrace trace : repository.saved) {
            assertThat(trace.spans()).hasSize(5).extracting(FlowSpan::traceId).containsOnly(trace.traceId());
        }
    }

    @Test
    void ignoresCallsOutsideATrackedRequest() {
        assertThat(recorder.isTracing()).isFalse();
        assertThat(recorder.openSpan("X", "y", ComponentType.SERVICE, null, null)).isNull();
        recorder.closeSpan(null, null);
        recorder.tag("k", "v");
        assertThat(publisher.events).isEmpty();
    }

    @Test
    void boundsMemoryByLimitingInFlightTracesAndSpans() {
        FlowRecorder small = new FlowRecorder(publisher, writer, RecordingSupport.fixedClock(), ticker::read, 1, 2);
        ActiveTrace trace = small.beginTrace("t-1", "GET", "/x", null).orElseThrow();
        for (int i = 0; i < 4; i++) {
            small.closeSpan(small.openSpan("C", "op", ComponentType.SERVICE, null, null), null);
        }
        FlowTrace snapshot = small.find("t-1").orElseThrow();
        assertThat(snapshot.spans()).hasSize(2);
        assertThat(snapshot.truncated()).isTrue();

        CompletableFuture<Boolean> other = CompletableFuture.supplyAsync(
                () -> small.beginTrace("t-2", "GET", "/x", null).isPresent());
        assertThat(other.join()).isFalse();
        assertThat(small.droppedTraces()).isEqualTo(1);
        small.endTrace(trace, 200, null, null);
    }

    @Test
    void exposesInFlightTracesUntilPersisted() {
        repository.failOnSave = false;
        ActiveTrace trace = recorder.beginTrace("t-live", "GET", "/api/products", null).orElseThrow();
        assertThat(recorder.find("t-live")).get().extracting(FlowTrace::status).isEqualTo(TraceStatus.RUNNING);
        recorder.endTrace(trace, 200, null, null);

        await(() -> recorder.find("t-live").isEmpty());
        assertThat(repository.saved).hasSize(1);
    }

    @Test
    void persistenceFailureDoesNotLeakInFlightTraces() {
        repository.failOnSave = true;
        ActiveTrace trace = recorder.beginTrace("t-fail", "GET", "/api/products", null).orElseThrow();
        recorder.endTrace(trace, 200, null, null);

        await(() -> recorder.inFlightCount() == 0);
        assertThat(publisher.events).extracting(FlowEvent::type).doesNotContain(FlowEventType.TRACE_PERSISTED);
    }

    @Test
    void tagsTheInnermostOpenSpanOnly() {
        ActiveTrace trace = recorder.beginTrace("t-tag", "POST", "/api/orders", null).orElseThrow();
        ActiveSpan outer = recorder.openSpan("A", "a", ComponentType.SERVICE, null, null);
        ActiveSpan inner = recorder.openSpan("B", "b", ComponentType.SERVICE, null, null);
        recorder.tag("demo.artificialDelayMs", "1500");
        recorder.closeSpan(inner, null);
        recorder.closeSpan(outer, null);
        recorder.endTrace(trace, 201, null, null);

        await(() -> repository.saved.size() == 1);
        List<FlowSpan> spans = repository.saved.getFirst().spans();
        assertThat(spans.get(0).tags()).isEmpty();
        assertThat(spans.get(1).tags()).containsEntry("demo.artificialDelayMs", "1500");
    }

    private static void runNamed(String name, Runnable runnable) {
        Thread thread = Thread.currentThread();
        String original = thread.getName();
        thread.setName(name);
        try {
            runnable.run();
        } finally {
            thread.setName(original);
        }
    }
}
