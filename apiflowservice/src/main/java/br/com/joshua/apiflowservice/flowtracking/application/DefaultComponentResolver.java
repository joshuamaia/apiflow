package br.com.joshua.apiflowservice.flowtracking.application;

import br.com.joshua.apiflowservice.flowtracking.api.ComponentIdentity;
import br.com.joshua.apiflowservice.flowtracking.api.ComponentResolver;
import br.com.joshua.apiflowservice.flowtracking.api.FlowTrack;

import java.lang.reflect.Method;

/** Uses the annotation attributes, falling back to the class simple name and the method name. */
public class DefaultComponentResolver implements ComponentResolver {

    @Override
    public ComponentIdentity resolve(FlowTrack annotation, Method method, Class<?> targetClass) {
        String component = annotation.component().isBlank() ? simpleName(targetClass) : annotation.component();
        String operation = annotation.operation().isBlank() ? method.getName() : annotation.operation();
        return new ComponentIdentity(component, operation, annotation.type());
    }

    private static String simpleName(Class<?> type) {
        String name = type.getSimpleName();
        int proxyMarker = name.indexOf("$$");
        return proxyMarker > 0 ? name.substring(0, proxyMarker) : name;
    }
}
