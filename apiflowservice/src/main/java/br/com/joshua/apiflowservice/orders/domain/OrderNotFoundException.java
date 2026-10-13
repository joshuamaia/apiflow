package br.com.joshua.apiflowservice.orders.domain;

import br.com.joshua.apiflowservice.shared.domain.ResourceNotFoundException;

public class OrderNotFoundException extends ResourceNotFoundException {

    public OrderNotFoundException(Long orderId) {
        super("ORDER_NOT_FOUND", "Order %d not found".formatted(orderId));
    }
}
