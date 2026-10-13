package br.com.joshua.apiflowservice.products.domain;

import br.com.joshua.apiflowservice.shared.domain.BusinessException;

public class InsufficientStockException extends BusinessException {

    public InsufficientStockException(Long productId, String productName, int requested, int available) {
        super("INSUFFICIENT_STOCK", "Insufficient stock for product %d (%s): requested %d, available %d"
                .formatted(productId, productName, requested, available));
    }
}
