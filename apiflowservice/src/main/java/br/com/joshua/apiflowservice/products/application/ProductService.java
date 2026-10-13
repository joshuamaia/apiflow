package br.com.joshua.apiflowservice.products.application;

import br.com.joshua.apiflowservice.flowtracking.api.FlowTrack;
import br.com.joshua.apiflowservice.flowtracking.domain.ComponentType;
import br.com.joshua.apiflowservice.products.application.port.out.ProductRepository;
import br.com.joshua.apiflowservice.products.domain.Product;
import br.com.joshua.apiflowservice.products.domain.ProductNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/** Product catalogue operations. */
@Service
public class ProductService {

    private final ProductRepository repository;

    public ProductService(ProductRepository repository) {
        this.repository = repository;
    }

    @FlowTrack(type = ComponentType.SERVICE)
    @Transactional(readOnly = true)
    public Product getProduct(Long id) {
        return repository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
    }

    @FlowTrack(type = ComponentType.SERVICE)
    @Transactional(readOnly = true)
    public List<Product> listProducts() {
        return repository.findAll();
    }

    @FlowTrack(type = ComponentType.SERVICE)
    @Transactional
    public Product createProduct(String name, BigDecimal price, int stock) {
        return repository.save(Product.create(name, price, stock));
    }
}
