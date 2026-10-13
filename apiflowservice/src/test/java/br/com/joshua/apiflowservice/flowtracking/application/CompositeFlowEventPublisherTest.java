package br.com.joshua.apiflowservice.flowtracking.application;

import br.com.joshua.apiflowservice.flowtracking.api.FlowEventListener;
import br.com.joshua.apiflowservice.flowtracking.domain.FlowEvent;
import br.com.joshua.apiflowservice.flowtracking.domain.FlowEventType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class CompositeFlowEventPublisherTest {

    @Test
    void deliversToEveryListenerEvenWhenOneFails() {
        List<FlowEvent> received = new ArrayList<>();
        FlowEventListener broken = event -> {
            throw new IllegalStateException("listener bug");
        };
        CompositeFlowEventPublisher publisher = new CompositeFlowEventPublisher(List.of(broken, received::add));
        FlowEvent event = new FlowEvent(1, FlowEventType.TRACE_STARTED, "t", Instant.now(), null, null);

        assertThatCode(() -> publisher.publish(event)).doesNotThrowAnyException();
        assertThat(received).containsExactly(event);
    }
}
