package br.com.joshua.apiflowservice.flowtracking.api;

import br.com.joshua.apiflowservice.flowtracking.domain.FlowEvent;

/**
 * Event capture contract. Every bean implementing it receives the lifecycle events of all traces.
 * <p>
 * Called on the request thread: implementations must be fast and non-blocking (hand the event off to
 * their own bounded queue). Exceptions thrown here are logged and ignored.
 */
public interface FlowEventListener {

    void onEvent(FlowEvent event);
}
