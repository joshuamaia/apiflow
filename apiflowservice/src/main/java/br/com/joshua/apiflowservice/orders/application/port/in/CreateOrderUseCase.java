package br.com.joshua.apiflowservice.orders.application.port.in;

import br.com.joshua.apiflowservice.orders.domain.Order;

import java.util.List;

public interface CreateOrderUseCase {

    Order execute(CreateOrderCommand command);

    record CreateOrderCommand(List<Line> lines) {
        public CreateOrderCommand {
            if (lines == null || lines.isEmpty()) {
                throw new IllegalArgumentException("An order needs at least one item");
            }
            lines = List.copyOf(lines);
        }
    }

    record Line(Long productId, int quantity) {
    }
}
