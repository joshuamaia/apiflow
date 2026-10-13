package br.com.joshua.apiflowservice.orders.application;

import br.com.joshua.apiflowservice.flowtracking.api.FlowTrack;
import br.com.joshua.apiflowservice.flowtracking.domain.ComponentType;
import br.com.joshua.apiflowservice.orders.application.port.in.CreateOrderUseCase;
import br.com.joshua.apiflowservice.orders.application.port.out.OrderRepository;
import br.com.joshua.apiflowservice.orders.domain.Order;
import br.com.joshua.apiflowservice.orders.domain.OrderItem;
import br.com.joshua.apiflowservice.products.application.ProductService;
import br.com.joshua.apiflowservice.products.application.StockService;
import br.com.joshua.apiflowservice.products.domain.Product;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/**
 * Places an order: loads each product, reserves its stock and persists the order – all in one
 * transaction, so a stock failure on any line rolls back the reservations already made.
 */
@Service
public class CreateOrderService implements CreateOrderUseCase {

    private final ProductService productService;
    private final StockService stockService;
    private final OrderRepository orderRepository;
    private final Clock clock;

    public CreateOrderService(ProductService productService, StockService stockService,
                              OrderRepository orderRepository, Clock clock) {
        this.productService = productService;
        this.stockService = stockService;
        this.orderRepository = orderRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    @FlowTrack(type = ComponentType.USE_CASE, component = "CreateOrderUseCase")
    public Order execute(CreateOrderCommand command) {
        List<OrderItem> items = new ArrayList<>();
        for (Line line : command.lines()) {
            Product product = productService.getProduct(line.productId());
            stockService.reserveStock(product, line.quantity());
            items.add(OrderItem.of(product.id(), line.quantity(), product.price()));
        }
        return orderRepository.save(Order.place(items, clock.instant()));
    }
}
