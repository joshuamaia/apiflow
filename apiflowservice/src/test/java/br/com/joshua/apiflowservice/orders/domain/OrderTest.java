package br.com.joshua.apiflowservice.orders.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    @Test
    void computesTotalFromItems() {
        Order order = Order.place(List.of(
                OrderItem.of(1L, 1, new BigDecimal("349.90")),
                OrderItem.of(2L, 2, new BigDecimal("129.90"))), Instant.EPOCH);

        assertThat(order.total()).isEqualByComparingTo("609.70");
        assertThat(order.status()).isEqualTo(OrderStatus.CREATED);
    }

    @Test
    void requiresItems() {
        assertThatThrownBy(() -> Order.place(List.of(), Instant.EPOCH)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requiresPositiveQuantity() {
        assertThatThrownBy(() -> OrderItem.of(1L, 0, BigDecimal.TEN)).isInstanceOf(IllegalArgumentException.class);
    }
}
