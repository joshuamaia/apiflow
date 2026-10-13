package br.com.joshua.apiflowservice.products.domain;

import java.math.BigDecimal;
import java.util.Objects;

/** A sellable product and its available stock. Pure domain object. */
public final class Product {

    private final Long id;
    private final String name;
    private final BigDecimal price;
    private final int stock;

    public Product(Long id, String name, BigDecimal price, int stock) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Product name is required");
        }
        if (price == null || price.signum() <= 0) {
            throw new IllegalArgumentException("Product price must be positive");
        }
        if (stock < 0) {
            throw new IllegalArgumentException("Product stock cannot be negative");
        }
        this.id = id;
        this.name = name.strip();
        this.price = price;
        this.stock = stock;
    }

    public static Product create(String name, BigDecimal price, int stock) {
        return new Product(null, name, price, stock);
    }

    public boolean hasStock(int quantity) {
        return quantity > 0 && stock >= quantity;
    }

    /** @throws InsufficientStockException when the requested quantity exceeds the available stock */
    public void ensureAvailable(int quantity) {
        if (!hasStock(quantity)) {
            throw new InsufficientStockException(id, name, quantity, stock);
        }
    }

    public Long id() {
        return id;
    }

    public String name() {
        return name;
    }

    public BigDecimal price() {
        return price;
    }

    public int stock() {
        return stock;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Product other && id != null && Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
