package br.com.joshua.apiflowservice.flowtracking.adapter.in.aop;

import br.com.joshua.apiflowservice.flowtracking.api.ComponentIdentity;
import br.com.joshua.apiflowservice.flowtracking.api.ComponentResolver;
import br.com.joshua.apiflowservice.flowtracking.api.FlowScope;
import br.com.joshua.apiflowservice.flowtracking.api.FlowTrack;
import br.com.joshua.apiflowservice.flowtracking.api.FlowTracker;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.Ordered;

/**
 * Wraps every {@link FlowTrack @FlowTrack} method in a flow span. Runs outside the transaction advice so
 * that commit time is part of the measured duration. Instrumentation problems never reach the business
 * call: if the span cannot be opened the method simply runs untracked.
 */
@Aspect
public class FlowTrackAspect implements Ordered {

    private static final Logger log = LoggerFactory.getLogger(FlowTrackAspect.class);

    private final FlowTracker tracker;
    private final ComponentResolver resolver;

    public FlowTrackAspect(FlowTracker tracker, ComponentResolver resolver) {
        this.tracker = tracker;
        this.resolver = resolver;
    }

    @Around("@annotation(flowTrack)")
    public Object track(ProceedingJoinPoint joinPoint, FlowTrack flowTrack) throws Throwable {
        if (!tracker.isTracing()) {
            return joinPoint.proceed();
        }
        FlowScope scope = openScope(joinPoint, flowTrack);
        try {
            return joinPoint.proceed();
        } catch (Throwable error) {
            scope.markError(error);
            throw error;
        } finally {
            scope.close();
        }
    }

    private FlowScope openScope(ProceedingJoinPoint joinPoint, FlowTrack flowTrack) {
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Class<?> targetClass = AopUtils.getTargetClass(joinPoint.getTarget());
            ComponentIdentity identity = resolver.resolve(flowTrack, signature.getMethod(), targetClass);
            return tracker.startSpan(identity.component(), identity.operation(), identity.type());
        } catch (RuntimeException e) {
            log.warn("Flow instrumentation failed for {}; continuing untracked", joinPoint.getSignature(), e);
            return FlowScope.NOOP;
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }
}
