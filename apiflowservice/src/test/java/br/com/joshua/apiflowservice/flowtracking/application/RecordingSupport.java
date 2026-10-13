package br.com.joshua.apiflowservice.flowtracking.application;

import br.com.joshua.apiflowservice.flowtracking.api.FlowEventPublisher;
import br.com.joshua.apiflowservice.flowtracking.api.TraceRepository;
import br.com.joshua.apiflowservice.flowtracking.domain.FlowEvent;
import br.com.joshua.apiflowservice.flowtracking.domain.FlowTrace;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

/** Test doubles shared by the tracking unit tests. */
public final class RecordingSupport {

    private RecordingSupport() {
    }

    public static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");

    /** Manually advanced monotonic clock. */
    public static final class FakeTicker {
        private final AtomicLong nanos = new AtomicLong(1_000_000_000L);

        public long read() {
            return nanos.get();
        }

        public void advanceMicros(long micros) {
            nanos.addAndGet(TimeUnit.MICROSECONDS.toNanos(micros));
        }
    }

    public static final class RecordingPublisher implements FlowEventPublisher {
        public final List<FlowEvent> events = new CopyOnWriteArrayList<>();

        @Override
        public void publish(FlowEvent event) {
            events.add(event);
        }
    }

    public static final class InMemoryTraceRepository implements TraceRepository {
        public final List<FlowTrace> saved = new CopyOnWriteArrayList<>();
        public volatile boolean failOnSave;

        @Override
        public void save(FlowTrace trace) {
            if (failOnSave) {
                throw new IllegalStateException("database down");
            }
            saved.add(trace);
        }

        @Override
        public int applyRetention(int maxTraces, Instant cutoff) {
            return 0;
        }

        @Override
        public int deleteAll() {
            int size = saved.size();
            saved.clear();
            return size;
        }
    }

    public static Clock fixedClock() {
        return Clock.fixed(T0, ZoneOffset.UTC);
    }

    public static void await(BooleanSupplier condition) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Condition not met within 5 seconds");
            }
            Thread.onSpinWait();
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        }
    }
}
