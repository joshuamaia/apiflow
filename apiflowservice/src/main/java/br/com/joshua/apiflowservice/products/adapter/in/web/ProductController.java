package br.com.joshua.apiflowservice.products.adapter.in.web;

import br.com.joshua.apiflowservice.flowtracking.api.FlowTrack;
import br.com.joshua.apiflowservice.flowtracking.domain.ComponentType;
import br.com.joshua.apiflowservice.products.adapter.in.web.dto.CreateProductRequest;
import br.com.joshua.apiflowservice.products.adapter.in.web.dto.ProductResponse;
import br.com.joshua.apiflowservice.products.application.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    @FlowTrack(type = ComponentType.CONTROLLER)
    public List<ProductResponse> list() {
        return productService.listProducts().stream().map(ProductResponse::from).toList();
    }

    @GetMapping("/{id}")
    @FlowTrack(type = ComponentType.CONTROLLER)
    public ProductResponse get(@PathVariable Long id) {
        return ProductResponse.from(productService.getProduct(id));
    }

    @PostMapping
    @FlowTrack(type = ComponentType.CONTROLLER)
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody CreateProductRequest request) {
        ProductResponse created = ProductResponse.from(
                productService.createProduct(request.name(), request.price(), request.stock()));
        return ResponseEntity.created(URI.create("/api/products/" + created.id())).body(created);
    }
}
