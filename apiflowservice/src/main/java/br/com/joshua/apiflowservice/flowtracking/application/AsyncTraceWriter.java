package br.com.joshua.apiflowservice.flowtracking.application;

import br.com.joshua.apiflowservice.flowtracking.api.TraceRepository;
import br.com.joshua.apiflowservice.flowtracking.domain.FlowTrace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Persists finished traces on a single background thread so the request thread never waits for the
 * database. The queue is bounded: when full, new traces are dropped (and counted) instead of growing
 * memory without limit.
 */
public class AsyncTraceWriter implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(AsyncTraceWriter.class);

    private record Task(FlowTrace trace, Consumer<Boolean> callback) {
    }

    private final TraceRepository repository;
    private final BlockingQueue<Task> queue;
    private final AtomicLong dropped = new AtomicLong();
    private volatile boolean running;
    private Thread worker;

    public AsyncTraceWriter(TraceRepository repository, int capacity) {
        this.repository = repository;
        this.queue = new ArrayBlockingQueue<>(capacity);
    }

    /**
     * @param callback invoked on the writer thread with {@code true} once persisted, {@code false} on failure
     * @return {@code false} when the trace was rejected (writer stopped or queue full)
     */
    public boolean submit(FlowTrace trace, Consumer<Boolean> callback) {
        if (!running || !queue.offer(new Task(trace, callback))) {
            dropped.incrementAndGet();
            log.debug("Trace {} dropped: writer stopped or persistence queue full", trace.traceId());
            return false;
        }
        return true;
    }

    private void loop() {
        while (running || !queue.isEmpty()) {
            try {
                Task task = queue.poll(250, TimeUnit.MILLISECONDS);
                if (task != null) {
                    persist(task);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void persist(Task task) {
        boolean persisted = false;
        try {
            repository.save(task.trace());
            persisted = true;
        } catch (RuntimeException e) {
            log.warn("Failed to persist trace {}: {}", task.trace().traceId(), e.getMessage());
        }
        try {
            task.callback().accept(persisted);
        } catch (RuntimeException e) {
            log.warn("Trace persistence callback failed", e);
        }
    }

    public long droppedCount() {
        return dropped.get();
    }

    public int pendingCount() {
        return queue.size();
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        worker = Thread.ofPlatform().name("flow-trace-writer").daemon(true).start(this::loop);
    }

    @Override
    public synchronized void stop() {
        running = false;
        if (worker != null) {
            try {
                worker.join(TimeUnit.SECONDS.toMillis(5));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
