package br.com.joshua.apiflowservice.products.adapter.out.persistence;

import br.com.joshua.apiflowservice.flowtracking.api.FlowTrack;
import br.com.joshua.apiflowservice.flowtracking.api.FlowTracker;
import br.com.joshua.apiflowservice.flowtracking.domain.ComponentType;
import br.com.joshua.apiflowservice.products.application.port.out.ProductRepository;
import br.com.joshua.apiflowservice.products.domain.Product;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * JPA implementation of {@link ProductRepository}. Each method is a REPOSITORY span, and the statement
 * that actually reaches PostgreSQL is wrapped in an explicit DATABASE span – JPA does not expose SQL
 * statements as spans by itself.
 */
@Component
class ProductPersistenceAdapter implements ProductRepository {

    private static final String DATABASE = "PostgreSQL";

    private final ProductJpaRepository jpa;
    private final FlowTracker tracker;

    ProductPersistenceAdapter(ProductJpaRepository jpa, FlowTracker tracker) {
        this.jpa = jpa;
        this.tracker = tracker;
    }

    @Override
    @FlowTrack(type = ComponentType.REPOSITORY, component = "ProductRepository")
    public Optional<Product> findById(Long id) {
        return tracker.track(DATABASE, "SELECT products by id", ComponentType.DATABASE, () -> jpa.findById(id))
                .map(ProductPersistenceAdapter::toDomain);
    }

    @Override
    @FlowTrack(type = ComponentType.REPOSITORY, component = "ProductRepository")
    public List<Product> findAll() {
        return tracker.track(DATABASE, "SELECT products", ComponentType.DATABASE,
                        () -> jpa.findAll(Sort.by("id")))
                .stream().map(ProductPersistenceAdapter::toDomain).toList();
    }

    @Override
    @FlowTrack(type = ComponentType.REPOSITORY, component = "ProductRepository")
    public Product save(Product product) {
        ProductJpaEntity entity = new ProductJpaEntity(product.id(), product.name(), product.price(), product.stock());
        return toDomain(tracker.track(DATABASE, "INSERT products", ComponentType.DATABASE,
                () -> jpa.saveAndFlush(entity)));
    }

    @Override
    @FlowTrack(type = ComponentType.REPOSITORY, component = "ProductRepository")
    public boolean decrementStock(Long productId, int quantity) {
        int updated = tracker.track(DATABASE, "UPDATE products.stock", ComponentType.DATABASE,
                () -> jpa.decrementStock(productId, quantity));
        return updated == 1;
    }

    private static Product toDomain(ProductJpaEntity entity) {
        return new Product(entity.getId(), entity.getName(), entity.getPrice(), entity.getStock());
    }
}
