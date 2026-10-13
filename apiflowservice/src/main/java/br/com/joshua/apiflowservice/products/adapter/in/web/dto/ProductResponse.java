package br.com.joshua.apiflowservice.products.adapter.in.web.dto;

import br.com.joshua.apiflowservice.products.domain.Product;

import java.math.BigDecimal;

public record ProductResponse(Long id, String name, BigDecimal price, int stock) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(product.id(), product.name(), product.price(), product.stock());
    }
}
