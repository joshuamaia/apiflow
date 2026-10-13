package br.com.joshua.apiflowservice.shared.demo;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;

/** Reads the demo latency headers and exposes them to {@link DemoLatency} for the current request. */
@Component
public class DemoLatencyFilter extends OncePerRequestFilter {

    public static final String DELAY_HEADER = "X-Demo-Delay-Ms";
    public static final String TARGET_HEADER = "X-Demo-Delay-Target";

    private static final Pattern SAFE_TARGET = Pattern.compile("[A-Za-z0-9_$. -]{1,200}");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getHeader(DELAY_HEADER) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String target = request.getHeader(TARGET_HEADER);
        try {
            DemoLatency.request(Long.parseLong(request.getHeader(DELAY_HEADER).strip()),
                    target != null && SAFE_TARGET.matcher(target).matches() ? target : null);
        } catch (NumberFormatException ignored) {
            // invalid header: no delay
        }
        try {
            chain.doFilter(request, response);
        } finally {
            DemoLatency.clear();
        }
    }
}
