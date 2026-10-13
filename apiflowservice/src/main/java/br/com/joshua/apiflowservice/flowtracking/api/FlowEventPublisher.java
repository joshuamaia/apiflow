package br.com.joshua.apiflowservice.flowtracking.api;

import br.com.joshua.apiflowservice.flowtracking.domain.FlowEvent;

/** Event publication contract used by the recorder to notify listeners. */
public interface FlowEventPublisher {

    void publish(FlowEvent event);
}
