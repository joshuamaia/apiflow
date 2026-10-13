package br.com.joshua.apiflowservice.orders.adapter.in.demo;

import br.com.joshua.apiflowservice.flowtracking.adapter.in.web.FlowTrackingFilter;
import br.com.joshua.apiflowservice.products.application.ProductService;
import br.com.joshua.apiflowservice.products.domain.Product;
import br.com.joshua.apiflowservice.shared.demo.DemoLatency;
import br.com.joshua.apiflowservice.shared.demo.DemoLatencyFilter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Runs the demo scenarios by sending real HTTP requests to this same application's
 * {@code POST /api/orders} endpoint. Nothing is simulated: the trace shown in the dashboard is the one
 * captured while that request was served.
 */
@Validated
@RestController
@RequestMapping("/api/demo/scenarios")
public class DemoScenarioController {

    private final ProductService productService;
    private final Environment environment;
    private final RestClient restClient = RestClient.create();

    public DemoScenarioController(ProductService productService, Environment environment) {
        this.productService = productService;
        this.environment = environment;
    }

    public record ScenarioInfo(String id, String description) {
    }

    public record ScenarioResult(String scenario, String description, String traceId, int httpStatus,
                                 long clientDurationMs, Map<String, Object> requestBody, Long artificialDelayMs) {
    }

    @GetMapping
    public List<ScenarioInfo> list() {
        return Arrays.stream(DemoScenario.values()).map(s -> new ScenarioInfo(s.slug(), s.description())).toList();
    }

    @PostMapping("/{scenario}")
    public ResponseEntity<ScenarioResult> run(@PathVariable String scenario,
                                              @RequestParam(defaultValue = "1500") @Min(0) @Max(5000) long delayMs) {
        DemoScenario demo = DemoScenario.fromSlug(scenario)
                .orElseThrow(() -> new IllegalArgumentException("Unknown scenario"));
        List<Product> products = productService.listProducts();
        if (products.size() < 2) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        Map<String, Object> body = requestBodyFor(demo, products);
        Long delay = demo == DemoScenario.SLOW ? delayMs : null;

        long start = System.nanoTime();
        ScenarioResult result = restClient.post()
                .uri("http://localhost:" + localPort() + "/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .header(FlowTrackingFilter.SCENARIO_HEADER, demo.slug())
                .headers(h -> {
                    if (delay != null) {
                        h.set(DemoLatencyFilter.DELAY_HEADER, String.valueOf(delay));
                        h.set(DemoLatencyFilter.TARGET_HEADER, DemoLatency.DEFAULT_TARGET);
                    }
                })
                .body(body)
                .exchange((request, response) -> new ScenarioResult(demo.slug(), demo.description(),
                        response.getHeaders().getFirst(FlowTrackingFilter.TRACE_ID_HEADER),
                        response.getStatusCode().value(), (System.nanoTime() - start) / 1_000_000, body, delay));
        return ResponseEntity.ok(result);
    }

    private static Map<String, Object> requestBodyFor(DemoScenario scenario, List<Product> products) {
        List<Product> byStock = products.stream().sorted(Comparator.comparingInt(Product::stock).reversed()).toList();
        Product first = byStock.get(0);
        Product second = byStock.get(1);
        if (scenario == DemoScenario.STOCK_ERROR) {
            Product limited = byStock.getLast();
            return Map.of("items", List.of(
                    Map.of("productId", first.id(), "quantity", 1),
                    Map.of("productId", limited.id(), "quantity", limited.stock() + 1)));
        }
        return Map.of("items", List.of(
                Map.of("productId", first.id(), "quantity", 1),
                Map.of("productId", second.id(), "quantity", 2)));
    }

    private String localPort() {
        return environment.getProperty("local.server.port", environment.getProperty("server.port", "8080"));
    }
}
