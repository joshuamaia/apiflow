package br.com.joshua.apiflowservice.flowtracking.api;

import br.com.joshua.apiflowservice.flowtracking.domain.ComponentType;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a step of the request flow. Only annotated methods are tracked – the
 * instrumentation never sweeps whole packages.
 * <p>
 * Interception relies on Spring AOP proxies: the annotation must be placed on a public method of a
 * Spring bean and is only honoured when the call goes through the proxy (calls to {@code this} are not
 * intercepted). For such cases use {@link FlowTracker} explicitly.
 * <p>
 * Method arguments and return values are <strong>never</strong> captured.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface FlowTrack {

    /** Architectural role of the component. */
    ComponentType type();

    /** Component name shown in the graph. Defaults to the target class simple name. */
    String component() default "";

    /** Operation name shown in the graph. Defaults to the method name. */
    String operation() default "";
}
