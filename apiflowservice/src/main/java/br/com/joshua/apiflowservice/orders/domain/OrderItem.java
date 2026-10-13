package br.com.joshua.apiflowservice.orders.domain;

import java.math.BigDecimal;

/** A product line of an order, with the unit price frozen at order time. */
public record OrderItem(Long id, Long productId, int quantity, BigDecimal unitPrice) {

    public OrderItem {
        if (productId == null) {
            throw new IllegalArgumentException("Product is required");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        if (unitPrice == null || unitPrice.signum() <= 0) {
            throw new IllegalArgumentException("Unit price must be positive");
        }
    }

    public static OrderItem of(Long productId, int quantity, BigDecimal unitPrice) {
        return new OrderItem(null, productId, quantity, unitPrice);
    }

    public BigDecimal subtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
