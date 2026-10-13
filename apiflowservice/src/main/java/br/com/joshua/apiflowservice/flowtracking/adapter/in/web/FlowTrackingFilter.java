package br.com.joshua.apiflowservice.flowtracking.adapter.in.web;

import br.com.joshua.apiflowservice.flowtracking.application.ActiveTrace;
import br.com.joshua.apiflowservice.flowtracking.application.FlowRecorder;
import br.com.joshua.apiflowservice.flowtracking.application.IdGenerator;
import br.com.joshua.apiflowservice.flowtracking.application.TrackedPaths;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Opens a flow trace for every HTTP request whose path matches the configured include patterns. Runs
 * inside Spring's {@code ServerHttpObservationFilter}, so the trace id is the one Micrometer Tracing
 * assigned to the HTTP request. The trace id is returned to the caller in {@value #TRACE_ID_HEADER}.
 * <p>
 * Only the method, route pattern and status are recorded – never headers, query strings or bodies.
 */
public class FlowTrackingFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_HEADER = "X-Flow-Trace-Id";
    public static final String SCENARIO_HEADER = "X-Flow-Scenario";

    private static final Logger log = LoggerFactory.getLogger(FlowTrackingFilter.class);
    private static final Pattern SAFE_SCENARIO = Pattern.compile("[a-z0-9-]{1,40}");

    private final FlowRecorder recorder;
    private final Tracer tracer;
    private final TrackedPaths trackedPaths;

    public FlowTrackingFilter(FlowRecorder recorder, Tracer tracer, TrackedPaths trackedPaths) {
        this.recorder = recorder;
        this.tracer = tracer == null ? Tracer.NOOP : tracer;
        this.trackedPaths = trackedPaths;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !trackedPaths.isTracked(pathOf(request));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<ActiveTrace> trace = begin(request);
        if (trace.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        response.setHeader(TRACE_ID_HEADER, trace.get().traceId());
        Throwable failure = null;
        try {
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException e) {
            failure = e;
            throw e;
        } finally {
            end(trace.get(), request, response, failure);
        }
    }

    private Optional<ActiveTrace> begin(HttpServletRequest request) {
        try {
            return recorder.beginTrace(currentTraceId(), request.getMethod(), pathOf(request), scenarioOf(request));
        } catch (RuntimeException e) {
            log.warn("Could not start flow trace; request continues untracked", e);
            return Optional.empty();
        }
    }

    private void end(ActiveTrace trace, HttpServletRequest request, HttpServletResponse response, Throwable failure) {
        try {
            int status = failure != null && response.getStatus() < 400 ? 500 : response.getStatus();
            Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            recorder.endTrace(trace, status, pattern instanceof String route ? route : null, failure);
        } catch (RuntimeException e) {
            log.warn("Could not finish flow trace {}", trace.traceId(), e);
        }
    }

    private String currentTraceId() {
        Span span = tracer.currentSpan();
        if (span != null && !span.isNoop() && span.context().traceId() != null) {
            return span.context().traceId();
        }
        return IdGenerator.traceId();
    }

    private static String scenarioOf(HttpServletRequest request) {
        String scenario = request.getHeader(SCENARIO_HEADER);
        return scenario != null && SAFE_SCENARIO.matcher(scenario).matches() ? scenario : null;
    }

    private static String pathOf(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        return contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)
                ? uri.substring(contextPath.length()) : uri;
    }

}
