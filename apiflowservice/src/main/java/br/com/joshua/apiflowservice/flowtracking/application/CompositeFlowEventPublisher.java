package br.com.joshua.apiflowservice.flowtracking.application;

import br.com.joshua.apiflowservice.flowtracking.api.FlowEventListener;
import br.com.joshua.apiflowservice.flowtracking.api.FlowEventPublisher;
import br.com.joshua.apiflowservice.flowtracking.domain.FlowEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/** Fans each event out to every registered listener, isolating listener failures. */
public class CompositeFlowEventPublisher implements FlowEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(CompositeFlowEventPublisher.class);

    private final List<FlowEventListener> listeners;

    public CompositeFlowEventPublisher(List<FlowEventListener> listeners) {
        this.listeners = List.copyOf(listeners);
    }

    @Override
    public void publish(FlowEvent event) {
        for (FlowEventListener listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (RuntimeException e) {
                log.warn("Flow event listener {} failed on {}", listener.getClass().getSimpleName(), event.type(), e);
            }
        }
    }
}
