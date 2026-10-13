package br.com.joshua.apiflowservice.flowvisualization.adapter.in.web;

import br.com.joshua.apiflowservice.flowtracking.api.ComponentIdentity;
import br.com.joshua.apiflowservice.flowtracking.api.ComponentResolver;
import br.com.joshua.apiflowservice.flowtracking.api.FlowTrack;
import br.com.joshua.apiflowservice.flowtracking.application.TrackedPaths;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ValueConstants;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lists the HTTP endpoints of this application that are tracked, straight from the Spring MVC
 * mappings, so the dashboard can call any of them without hard-coded routes.
 */
@RestController
@RequestMapping("/api/flow")
public class EndpointCatalogController {

    private static final Pattern PATH_VARIABLE = Pattern.compile("\\{([^}:]+)(?::[^}]*)?}");
    private static final DefaultParameterNameDiscoverer NAMES = new DefaultParameterNameDiscoverer();

    private final RequestMappingHandlerMapping mappings;
    private final TrackedPaths trackedPaths;
    private final ComponentResolver componentResolver;

    public EndpointCatalogController(@Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings,
                                     TrackedPaths trackedPaths, ComponentResolver componentResolver) {
        this.mappings = mappings;
        this.trackedPaths = trackedPaths;
        this.componentResolver = componentResolver;
    }

    public record QueryParamDto(String name, boolean required, String defaultValue) {
    }

    /**
     * @param instrumented whether the handler method itself carries {@code @FlowTrack}
     * @param bodyExample  skeleton JSON generated from the {@code @RequestBody} type, or {@code null}
     * @param entryStep    {@code Component.operation} of the handler as shown in the graph, when instrumented
     */
    public record EndpointDto(String method, String path, List<String> pathVariables, List<QueryParamDto> queryParams,
                              boolean hasBody, Object bodyExample, String handler, boolean instrumented,
                              String entryStep) {
    }

    @GetMapping("/endpoints")
    public List<EndpointDto> endpoints() {
        List<EndpointDto> result = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : mappings.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = entry.getKey();
            HandlerMethod handler = entry.getValue();
            if (info.getPathPatternsCondition() == null) {
                continue;
            }
            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            for (String path : info.getPathPatternsCondition().getPatternValues()) {
                if (!trackedPaths.isTracked(path)) {
                    continue;
                }
                for (RequestMethod method : methods.isEmpty() ? Set.of(RequestMethod.GET) : methods) {
                    result.add(describe(method.name(), path, handler));
                }
            }
        }
        result.sort(Comparator.comparing(EndpointDto::path).thenComparing(EndpointDto::method));
        return result;
    }

    private EndpointDto describe(String method, String path, HandlerMethod handler) {
        List<String> pathVariables = new ArrayList<>();
        Matcher matcher = PATH_VARIABLE.matcher(path);
        while (matcher.find()) {
            pathVariables.add(matcher.group(1));
        }
        List<QueryParamDto> queryParams = new ArrayList<>();
        Object bodyExample = null;
        boolean hasBody = false;
        for (MethodParameter parameter : handler.getMethodParameters()) {
            RequestParam requestParam = parameter.getParameterAnnotation(RequestParam.class);
            if (requestParam != null) {
                parameter.initParameterNameDiscovery(NAMES);
                String name = !requestParam.name().isEmpty() ? requestParam.name() : parameter.getParameterName();
                String defaultValue = ValueConstants.DEFAULT_NONE.equals(requestParam.defaultValue())
                        ? null : requestParam.defaultValue();
                queryParams.add(new QueryParamDto(name, requestParam.required() && defaultValue == null, defaultValue));
            }
            if (parameter.hasParameterAnnotation(RequestBody.class)) {
                hasBody = true;
                bodyExample = JsonSample.of(parameter.getGenericParameterType());
            }
        }
        String handlerName = handler.getBeanType().getSimpleName() + "." + handler.getMethod().getName();
        FlowTrack flowTrack = handler.getMethodAnnotation(FlowTrack.class);
        return new EndpointDto(method, path, pathVariables, queryParams, hasBody, bodyExample, handlerName,
                flowTrack != null, entryStep(flowTrack, handler));
    }

    /** Name of the handler's own step, resolved exactly as the instrumentation will name it. */
    private String entryStep(FlowTrack flowTrack, HandlerMethod handler) {
        if (flowTrack == null) {
            return null;
        }
        try {
            ComponentIdentity identity = componentResolver.resolve(flowTrack, handler.getMethod(), handler.getBeanType());
            return identity.component() + "." + identity.operation();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
