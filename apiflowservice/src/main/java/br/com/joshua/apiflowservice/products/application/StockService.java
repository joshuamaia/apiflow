package br.com.joshua.apiflowservice.products.application;

import br.com.joshua.apiflowservice.flowtracking.api.FlowTrack;
import br.com.joshua.apiflowservice.flowtracking.domain.ComponentType;
import br.com.joshua.apiflowservice.products.application.port.out.ProductRepository;
import br.com.joshua.apiflowservice.products.domain.InsufficientStockException;
import br.com.joshua.apiflowservice.products.domain.Product;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Validates and reserves product stock. */
@Service
public class StockService {

    private final ProductRepository repository;

    public StockService(ProductRepository repository) {
        this.repository = repository;
    }

    /**
     * Checks the stock of an already loaded product and reserves the quantity with an atomic conditional
     * update, so concurrent orders can never take the stock below zero.
     *
     * @throws InsufficientStockException when the product does not have enough stock
     */
    @FlowTrack(type = ComponentType.SERVICE)
    @Transactional
    public void reserveStock(Product product, int quantity) {
        product.ensureAvailable(quantity);
        if (!repository.decrementStock(product.id(), quantity)) {
            Product current = repository.findById(product.id()).orElse(product);
            throw new InsufficientStockException(product.id(), product.name(), quantity, current.stock());
        }
    }
}
