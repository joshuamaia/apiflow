package br.com.joshua.apiflowservice.flowvisualization.adapter.in.web;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Real-time event stream. Each SSE message carries one flow event whose {@code id} is the event
 * sequence, so browsers resume with {@code Last-Event-ID} after a disconnect.
 */
@RestController
@RequestMapping("/api/flow")
public class FlowStreamController {

    private final FlowEventBroadcaster broadcaster;

    public FlowStreamController(FlowEventBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    /**
     * @param lastEventId     sent by the browser on automatic reconnects
     * @param lastEventIdParam same value for manual reconnects ({@code EventSource} cannot set headers)
     */
    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestHeader(name = "Last-Event-ID", required = false) String lastEventId,
                             @RequestParam(name = "lastEventId", required = false) String lastEventIdParam,
                             HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
        return broadcaster.subscribe(parse(lastEventId != null ? lastEventId : lastEventIdParam));
    }

    private static Long parse(String value) {
        try {
            return value == null || value.isBlank() ? null : Long.valueOf(value.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
