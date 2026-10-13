package br.com.joshua.apiflowservice.products.application.port.out;

import br.com.joshua.apiflowservice.products.domain.Product;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {

    Optional<Product> findById(Long id);

    List<Product> findAll();

    Product save(Product product);

    /**
     * Atomically subtracts {@code quantity} from the stock, only if enough stock is available.
     *
     * @return {@code false} when the stock was not sufficient at the time of the update
     */
    boolean decrementStock(Long productId, int quantity);
}
