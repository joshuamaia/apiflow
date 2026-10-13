package br.com.joshua.apiflowservice.flowtracking.api;

import java.lang.reflect.Method;

/**
 * Component identification contract: turns an intercepted method into the name shown in the graph.
 * Provide your own bean to apply naming conventions of another code base.
 */
public interface ComponentResolver {

    ComponentIdentity resolve(FlowTrack annotation, Method method, Class<?> targetClass);
}
