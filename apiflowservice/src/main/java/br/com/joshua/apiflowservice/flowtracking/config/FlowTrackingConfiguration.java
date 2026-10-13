package br.com.joshua.apiflowservice.flowtracking.config;

import br.com.joshua.apiflowservice.flowtracking.adapter.in.aop.FlowTrackAspect;
import br.com.joshua.apiflowservice.flowtracking.adapter.in.web.FlowTrackingFilter;
import br.com.joshua.apiflowservice.flowtracking.api.ComponentResolver;
import br.com.joshua.apiflowservice.flowtracking.api.FlowEventListener;
import br.com.joshua.apiflowservice.flowtracking.api.FlowEventPublisher;
import br.com.joshua.apiflowservice.flowtracking.api.FlowTracker;
import br.com.joshua.apiflowservice.flowtracking.api.SpanStartHook;
import br.com.joshua.apiflowservice.flowtracking.api.TraceRepository;
import br.com.joshua.apiflowservice.flowtracking.application.AsyncTraceWriter;
import br.com.joshua.apiflowservice.flowtracking.application.CompositeFlowEventPublisher;
import br.com.joshua.apiflowservice.flowtracking.application.DefaultComponentResolver;
import br.com.joshua.apiflowservice.flowtracking.application.FlowRecorder;
import br.com.joshua.apiflowservice.flowtracking.application.MicrometerFlowTracker;
import br.com.joshua.apiflowservice.flowtracking.application.TrackedPaths;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.util.List;

/**
 * Wires the tracking mechanism. Everything here depends only on the {@code flowtracking} packages, so the
 * class can become the auto-configuration of a future Spring Boot starter.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(FlowTrackingProperties.class)
public class FlowTrackingConfiguration {

    private static final Logger log = LoggerFactory.getLogger(FlowTrackingConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    FlowEventPublisher flowEventPublisher(List<FlowEventListener> listeners) {
        return new CompositeFlowEventPublisher(listeners);
    }

    @Bean
    AsyncTraceWriter asyncTraceWriter(TraceRepository repository, FlowTrackingProperties properties) {
        return new AsyncTraceWriter(repository, properties.getPersistenceQueueCapacity());
    }

    @Bean
    FlowRecorder flowRecorder(FlowEventPublisher publisher, AsyncTraceWriter writer, FlowTrackingProperties properties) {
        return new FlowRecorder(publisher, writer, Clock.systemUTC(), System::nanoTime,
                properties.getMaxInFlightTraces(), properties.getMaxSpansPerTrace());
    }

    @Bean
    @ConditionalOnMissingBean
    FlowTracker flowTracker(FlowRecorder recorder, ObjectProvider<ObservationRegistry> observationRegistry,
                            ObjectProvider<Tracer> tracer, ObjectProvider<SpanStartHook> hooks) {
        return new MicrometerFlowTracker(recorder, observationRegistry.getIfAvailable(), tracer.getIfAvailable(),
                hooks.orderedStream().toList());
    }

    @Bean
    @ConditionalOnMissingBean
    ComponentResolver componentResolver() {
        return new DefaultComponentResolver();
    }

    @Bean
    FlowTrackAspect flowTrackAspect(FlowTracker tracker, ComponentResolver resolver) {
        return new FlowTrackAspect(tracker, resolver);
    }

    @Bean
    TrackedPaths trackedPaths(FlowTrackingProperties properties) {
        return new TrackedPaths(properties.getIncludePaths(), properties.getExcludePaths());
    }

    @Bean
    FilterRegistrationBean<FlowTrackingFilter> flowTrackingFilter(FlowRecorder recorder, ObjectProvider<Tracer> tracer,
                                                                 TrackedPaths trackedPaths, FlowTrackingProperties properties) {
        FlowTrackingFilter filter = new FlowTrackingFilter(recorder, tracer.getIfAvailable(), trackedPaths);
        FilterRegistrationBean<FlowTrackingFilter> registration = new FilterRegistrationBean<>(filter);
        // After ServerHttpObservationFilter (HIGHEST_PRECEDENCE + 1) so the HTTP trace context is available.
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registration.setEnabled(properties.isEnabled());
        return registration;
    }

    @Bean
    FlowTrackingRetention flowTrackingRetention(TraceRepository repository, FlowTrackingProperties properties) {
        return new FlowTrackingRetention(repository, properties);
    }

    /** Exposes the health of the tracking pipeline through Actuator metrics. */
    @Bean
    MeterBinder flowTrackingMeters(FlowRecorder recorder, AsyncTraceWriter writer) {
        return registry -> {
            Gauge.builder("flow.tracking.traces.in_flight", recorder, FlowRecorder::inFlightCount).register(registry);
            Gauge.builder("flow.tracking.traces.dropped", recorder, FlowRecorder::droppedTraces).register(registry);
            Gauge.builder("flow.tracking.persistence.pending", writer, AsyncTraceWriter::pendingCount).register(registry);
            Gauge.builder("flow.tracking.persistence.dropped", writer, AsyncTraceWriter::droppedCount).register(registry);
        };
    }

    /** Periodically enforces the retention limits of stored traces. */
    public static class FlowTrackingRetention {
        private final TraceRepository repository;
        private final FlowTrackingProperties properties;

        FlowTrackingRetention(TraceRepository repository, FlowTrackingProperties properties) {
            this.repository = repository;
            this.properties = properties;
        }

        @Scheduled(fixedDelayString = "${flow.tracking.retention.cleanup-interval:PT1M}",
                initialDelayString = "${flow.tracking.retention.cleanup-interval:PT1M}")
        public void apply() {
            try {
                FlowTrackingProperties.Retention retention = properties.getRetention();
                int removed = repository.applyRetention(retention.getMaxTraces(),
                        Clock.systemUTC().instant().minus(retention.getMaxAge()));
                if (removed > 0) {
                    log.info("Flow tracking retention removed {} traces", removed);
                }
            } catch (RuntimeException e) {
                log.warn("Flow tracking retention failed: {}", e.getMessage());
            }
        }
    }
}
