package br.com.joshua.apiflowservice.orders.adapter.in.web.dto;

import br.com.joshua.apiflowservice.orders.domain.Order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(Long id, Instant createdAt, String status, BigDecimal total, List<Item> items) {

    public record Item(Long productId, int quantity, BigDecimal unitPrice) {
    }

    public static OrderResponse from(Order order) {
        return new OrderResponse(order.id(), order.createdAt(), order.status().name(), order.total(),
                order.items().stream().map(i -> new Item(i.productId(), i.quantity(), i.unitPrice())).toList());
    }
}
