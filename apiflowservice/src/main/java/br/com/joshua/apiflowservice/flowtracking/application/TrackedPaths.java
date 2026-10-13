package br.com.joshua.apiflowservice.flowtracking.application;

import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.List;

/**
 * Decides which request paths open a trace: a path must match an include pattern and no exclude
 * pattern. Shared by the servlet filter and by anything that needs to know what is tracked.
 */
public class TrackedPaths {

    private final List<PathPattern> includes;
    private final List<PathPattern> excludes;

    public TrackedPaths(List<String> includePatterns, List<String> excludePatterns) {
        this.includes = parse(includePatterns);
        this.excludes = parse(excludePatterns);
    }

    /** @param path request path without context path, or a route pattern such as {@code /api/orders/{id}} */
    public boolean isTracked(String path) {
        PathContainer container = PathContainer.parsePath(path);
        return includes.stream().anyMatch(p -> p.matches(container))
                && excludes.stream().noneMatch(p -> p.matches(container));
    }

    private static List<PathPattern> parse(List<String> patterns) {
        return patterns == null ? List.of() : patterns.stream().map(PathPatternParser.defaultInstance::parse).toList();
    }
}
