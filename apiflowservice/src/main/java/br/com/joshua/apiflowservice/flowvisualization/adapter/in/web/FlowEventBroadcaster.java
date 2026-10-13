package br.com.joshua.apiflowservice.flowvisualization.adapter.in.web;

import br.com.joshua.apiflowservice.flowtracking.api.FlowEventListener;
import br.com.joshua.apiflowservice.flowtracking.domain.FlowEvent;
import br.com.joshua.apiflowservice.flowvisualization.adapter.in.web.dto.FlowDtos.FlowEventDto;
import br.com.joshua.apiflowservice.flowvisualization.config.VisualizationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Streams flow events to dashboard clients over Server-Sent Events.
 * <p>
 * Events are handed to a single dispatcher thread through a bounded queue, so the request thread that
 * produced them never blocks on a slow client and the delivery order is preserved. A ring buffer of
 * recent events lets a client that reconnects with {@code Last-Event-ID} catch up on what it missed.
 */
@Component
public class FlowEventBroadcaster implements FlowEventListener, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(FlowEventBroadcaster.class);
    private static final long RECONNECT_DELAY_MS = 2_000;

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final Deque<FlowEventDto> recentEvents = new ArrayDeque<>();
    private final ThreadPoolExecutor dispatcher;
    private final FlowDtoMapper mapper;
    private final VisualizationProperties properties;

    public FlowEventBroadcaster(FlowDtoMapper mapper, VisualizationProperties properties) {
        this.mapper = mapper;
        this.properties = properties;
        this.dispatcher = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(properties.getDispatchQueueCapacity()),
                Thread.ofPlatform().name("flow-sse-dispatcher").daemon(true).factory(),
                new ThreadPoolExecutor.DiscardOldestPolicy());
    }

    @Override
    public void onEvent(FlowEvent event) {
        FlowEventDto dto = mapper.event(event);
        synchronized (recentEvents) {
            recentEvents.addLast(dto);
            while (recentEvents.size() > properties.getReplayBufferSize()) {
                recentEvents.removeFirst();
            }
        }
        dispatcher.execute(() -> broadcast(dto));
    }

    /**
     * Registers a new subscriber. When {@code lastEventId} is given, buffered events newer than it are
     * replayed before live events, in order.
     */
    public SseEmitter subscribe(Long lastEventId) {
        SseEmitter emitter = new SseEmitter(properties.getSseTimeout().toMillis());
        if (emitters.size() >= properties.getMaxSubscribers()) {
            emitter.completeWithError(new IllegalStateException("Too many subscribers"));
            return emitter;
        }
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(emitter::complete);
        emitter.onError(error -> emitters.remove(emitter));
        dispatcher.execute(() -> register(emitter, lastEventId));
        return emitter;
    }

    private void register(SseEmitter emitter, Long lastEventId) {
        try {
            List<FlowEventDto> missed = new ArrayList<>();
            boolean gap = false;
            synchronized (recentEvents) {
                if (lastEventId != null) {
                    FlowEventDto oldest = recentEvents.peekFirst();
                    gap = oldest != null && oldest.sequence() > lastEventId + 1;
                    recentEvents.stream().filter(e -> e.sequence() > lastEventId).forEach(missed::add);
                }
            }
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .reconnectTime(RECONNECT_DELAY_MS)
                    .data(Map.of("serverTime", Instant.now().toString(), "replayed", missed.size(),
                            "gap", gap), MediaType.APPLICATION_JSON));
            for (FlowEventDto event : missed) {
                send(emitter, event);
            }
            emitters.add(emitter);
        } catch (IOException | IllegalStateException e) {
            emitter.completeWithError(e);
        }
    }

    private void broadcast(FlowEventDto event) {
        for (SseEmitter emitter : emitters) {
            try {
                send(emitter, event);
            } catch (IOException | IllegalStateException e) {
                emitters.remove(emitter);
                log.debug("Removing SSE subscriber: {}", e.getMessage());
            }
        }
    }

    private static void send(SseEmitter emitter, FlowEventDto event) throws IOException {
        emitter.send(SseEmitter.event()
                .id(Long.toString(event.sequence()))
                .data(event, MediaType.APPLICATION_JSON));
    }

    /** Keeps idle connections open through proxies and detects dead clients. */
    @Scheduled(fixedRate = 15, timeUnit = TimeUnit.SECONDS)
    public void heartbeat() {
        dispatcher.execute(() -> {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().comment("heartbeat"));
                } catch (IOException | IllegalStateException e) {
                    emitters.remove(emitter);
                }
            }
        });
    }

    public int subscriberCount() {
        return emitters.size();
    }

    @Override
    public void destroy() {
        emitters.forEach(SseEmitter::complete);
        emitters.clear();
        dispatcher.shutdownNow();
    }
}
