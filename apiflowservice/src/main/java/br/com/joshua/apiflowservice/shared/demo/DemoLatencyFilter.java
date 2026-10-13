package br.com.joshua.apiflowservice.shared.demo;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Reads the demo delay header of order requests and exposes it to {@link DemoLatency}. */
@Component
public class DemoLatencyFilter extends OncePerRequestFilter {

    public static final String DELAY_HEADER = "X-Demo-Delay-Ms";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getHeader(DELAY_HEADER) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            DemoLatency.request(Long.parseLong(request.getHeader(DELAY_HEADER).strip()));
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
