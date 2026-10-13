package br.com.joshua.apiflowservice.orders.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** A placed order. The total is always derived from its items. */
public record Order(Long id, Instant createdAt, OrderStatus status, BigDecimal total, List<OrderItem> items) {

    public Order {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("An order needs at least one item");
        }
        items = List.copyOf(items);
    }

    public static Order place(List<OrderItem> items, Instant now) {
        BigDecimal total = items.stream().map(OrderItem::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Order(null, now, OrderStatus.CREATED, total, items);
    }
}
