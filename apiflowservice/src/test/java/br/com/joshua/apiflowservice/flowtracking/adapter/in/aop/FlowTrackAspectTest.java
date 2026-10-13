package br.com.joshua.apiflowservice.flowtracking.adapter.in.aop;

import br.com.joshua.apiflowservice.flowtracking.api.ComponentResolver;
import br.com.joshua.apiflowservice.flowtracking.api.FlowTrack;
import br.com.joshua.apiflowservice.flowtracking.api.SpanStartHook;
import br.com.joshua.apiflowservice.flowtracking.application.ActiveTrace;
import br.com.joshua.apiflowservice.flowtracking.application.AsyncTraceWriter;
import br.com.joshua.apiflowservice.flowtracking.application.DefaultComponentResolver;
import br.com.joshua.apiflowservice.flowtracking.application.FlowRecorder;
import br.com.joshua.apiflowservice.flowtracking.application.MicrometerFlowTracker;
import br.com.joshua.apiflowservice.flowtracking.application.RecordingSupport.InMemoryTraceRepository;
import br.com.joshua.apiflowservice.flowtracking.application.RecordingSupport.RecordingPublisher;
import br.com.joshua.apiflowservice.flowtracking.domain.ComponentType;
import br.com.joshua.apiflowservice.flowtracking.domain.FlowSpan;
import br.com.joshua.apiflowservice.flowtracking.domain.FlowTrace;
import br.com.joshua.apiflowservice.flowtracking.domain.SpanStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.time.Clock;
import java.util.List;

import static br.com.joshua.apiflowservice.flowtracking.application.RecordingSupport.await;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlowTrackAspectTest {

    private final InMemoryTraceRepository repository = new InMemoryTraceRepository();
    private AsyncTraceWriter writer;
    private FlowRecorder recorder;

    /** Sample component: the outer method calls the inner one through a separate proxied bean. */
    static class Inventory {
        @FlowTrack(type = ComponentType.SERVICE)
        public int available(String sku) {
            return 42;
        }

        @FlowTrack(type = ComponentType.SERVICE, component = "Inventory", operation = "fail")
        public int broken() {
            throw new IllegalArgumentException("unknown sku");
        }

        @FlowTrack(type = ComponentType.SERVICE)
        public int selfInvocation() {
            return available("x"); // not intercepted: proxy-based AOP limitation
        }
    }

    static class Checkout {
        private final Inventory inventory;

        Checkout(Inventory inventory) {
            this.inventory = inventory;
        }

        @FlowTrack(type = ComponentType.USE_CASE, component = "CheckoutUseCase")
        public int checkout() {
            return inventory.available("sku-1");
        }
    }

    @BeforeEach
    void setUp() {
        writer = new AsyncTraceWriter(repository, 10);
        writer.start();
        recorder = new FlowRecorder(new RecordingPublisher(), writer, Clock.systemUTC(), System::nanoTime, 10, 50);
    }

    @AfterEach
    void tearDown() {
        writer.stop();
    }

    @Test
    void createsNestedSpansForAnnotatedMethods() {
        FlowTrackAspect aspect = aspect(new DefaultComponentResolver());
        Inventory inventory = proxy(new Inventory(), aspect);
        Checkout checkout = proxy(new Checkout(inventory), aspect);

        FlowTrace trace = withinTrace(checkout::checkout);

        assertThat(trace.spans()).extracting(FlowSpan::component).containsExactly("CheckoutUseCase", "Inventory");
        assertThat(trace.spans()).extracting(FlowSpan::operation).containsExactly("checkout", "available");
        assertThat(trace.spans()).extracting(FlowSpan::type).containsExactly(ComponentType.USE_CASE, ComponentType.SERVICE);
        assertThat(trace.spans().get(1).parentSpanId()).isEqualTo(trace.spans().get(0).spanId());
        assertThat(trace.spans()).allSatisfy(span -> {
            assertThat(span.status()).isEqualTo(SpanStatus.COMPLETED);
            assertThat(span.durationMicros()).isNotNull().isGreaterThanOrEqualTo(0);
        });
    }

    @Test
    void recordsExceptionAndRethrowsIt() {
        Inventory inventory = proxy(new Inventory(), aspect(new DefaultComponentResolver()));

        ActiveTrace active = recorder.beginTrace("t-err", "GET", "/x", null).orElseThrow();
        assertThatThrownBy(inventory::broken).isInstanceOf(IllegalArgumentException.class).hasMessage("unknown sku");
        recorder.endTrace(active, 500, null, null);

        FlowSpan span = persisted().spans().getFirst();
        assertThat(span.status()).isEqualTo(SpanStatus.ERROR);
        assertThat(span.operation()).isEqualTo("fail");
        assertThat(span.errorType()).isEqualTo(IllegalArgumentException.class.getName());
        assertThat(span.errorMessage()).isEqualTo("unknown sku");
    }

    @Test
    void instrumentationFailureDoesNotBreakTheBusinessCall() {
        ComponentResolver broken = (annotation, method, type) -> {
            throw new IllegalStateException("resolver bug");
        };
        Inventory inventory = proxy(new Inventory(), aspect(broken));

        FlowTrace trace = withinTrace(() -> assertThat(inventory.available("sku")).isEqualTo(42));

        assertThat(trace.spans()).isEmpty();
    }

    @Test
    void selfInvocationIsNotIntercepted() {
        Inventory inventory = proxy(new Inventory(), aspect(new DefaultComponentResolver()));

        FlowTrace trace = withinTrace(inventory::selfInvocation);

        assertThat(trace.spans()).extracting(FlowSpan::operation).containsExactly("selfInvocation");
    }

    @Test
    void startHooksRunInsideTheStepAndCanTagIt() {
        SpanStartHook slowInventory = step -> {
            if (step.component().equals("Inventory")) {
                step.tag("demo.artificialDelayMs", "30");
                sleep(30);
            }
        };
        FlowTrackAspect aspect = new FlowTrackAspect(
                new MicrometerFlowTracker(recorder, null, null, List.of(slowInventory)), new DefaultComponentResolver());
        Checkout checkout = proxy(new Checkout(proxy(new Inventory(), aspect)), aspect);

        FlowTrace trace = withinTrace(checkout::checkout);

        FlowSpan inventory = trace.spans().get(1);
        assertThat(inventory.durationMicros()).isGreaterThanOrEqualTo(30_000);
        assertThat(inventory.tags()).containsEntry("demo.artificialDelayMs", "30");
        assertThat(trace.spans().get(0).tags()).isEmpty();
    }

    @Test
    void failingStartHookDoesNotBreakTheBusinessCall() {
        SpanStartHook broken = step -> {
            throw new IllegalStateException("hook bug");
        };
        FlowTrackAspect aspect = new FlowTrackAspect(
                new MicrometerFlowTracker(recorder, null, null, List.of(broken)), new DefaultComponentResolver());
        Inventory inventory = proxy(new Inventory(), aspect);

        FlowTrace trace = withinTrace(() -> assertThat(inventory.available("sku")).isEqualTo(42));

        assertThat(trace.spans()).extracting(FlowSpan::status).containsExactly(SpanStatus.COMPLETED);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void runsUntrackedOutsideARequest() {
        Inventory inventory = proxy(new Inventory(), aspect(new DefaultComponentResolver()));

        assertThat(inventory.available("sku")).isEqualTo(42);
        assertThat(recorder.inFlightCount()).isZero();
    }

    private FlowTrace withinTrace(Runnable call) {
        ActiveTrace active = recorder.beginTrace("t-" + System.nanoTime(), "GET", "/x", null).orElseThrow();
        call.run();
        recorder.endTrace(active, 200, null, null);
        return persisted();
    }

    private FlowTrace persisted() {
        await(() -> !repository.saved.isEmpty());
        return repository.saved.getLast();
    }

    private FlowTrackAspect aspect(ComponentResolver resolver) {
        return new FlowTrackAspect(new MicrometerFlowTracker(recorder, null, null), resolver);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(T target, FlowTrackAspect aspect) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAspect(aspect);
        return (T) factory.getProxy();
    }
}
