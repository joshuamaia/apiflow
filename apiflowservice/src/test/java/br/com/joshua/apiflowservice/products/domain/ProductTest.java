package br.com.joshua.apiflowservice.products.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductTest {

    private final Product headset = new Product(6L, "Limited Edition Headset", new BigDecimal("999.00"), 3);

    @Test
    void acceptsQuantitiesWithinStock() {
        assertThat(headset.hasStock(3)).isTrue();
        assertThatCode(() -> headset.ensureAvailable(3)).doesNotThrowAnyException();
    }

    @Test
    void rejectsQuantitiesAboveStock() {
        assertThat(headset.hasStock(4)).isFalse();
        assertThatThrownBy(() -> headset.ensureAvailable(4))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("requested 4, available 3");
    }

    @Test
    void rejectsNonPositiveQuantities() {
        assertThat(headset.hasStock(0)).isFalse();
    }

    @Test
    void validatesInvariants() {
        assertThatThrownBy(() -> Product.create(" ", BigDecimal.ONE, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Product.create("x", BigDecimal.ZERO, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Product.create("x", BigDecimal.ONE, -1)).isInstanceOf(IllegalArgumentException.class);
    }
}
