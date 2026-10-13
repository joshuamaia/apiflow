package br.com.joshua.apiflowservice.products.domain;

import br.com.joshua.apiflowservice.shared.domain.ResourceNotFoundException;

public class ProductNotFoundException extends ResourceNotFoundException {

    public ProductNotFoundException(Long productId) {
        super("PRODUCT_NOT_FOUND", "Product %d not found".formatted(productId));
    }
}
