package br.com.joshua.apiflowservice.orders.adapter.in.demo;

import java.util.Arrays;
import java.util.Optional;

/** The demonstration scenarios. Each one results in a real {@code POST /api/orders} request. */
public enum DemoScenario {

    SUCCESS("success", "Valid order with two products in stock"),
    STOCK_ERROR("stock-error", "Second line asks for more units than the limited product has in stock"),
    SLOW("slow", "Valid order with an artificial delay injected in StockService (demo only)");

    private final String slug;
    private final String description;

    DemoScenario(String slug, String description) {
        this.slug = slug;
        this.description = description;
    }

    public String slug() {
        return slug;
    }

    public String description() {
        return description;
    }

    public static Optional<DemoScenario> fromSlug(String slug) {
        return Arrays.stream(values()).filter(s -> s.slug.equalsIgnoreCase(slug)).findFirst();
    }
}
