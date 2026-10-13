package br.com.joshua.apiflowservice.orders.adapter.in.web;

import br.com.joshua.apiflowservice.flowtracking.api.FlowTrack;
import br.com.joshua.apiflowservice.flowtracking.domain.ComponentType;
import br.com.joshua.apiflowservice.orders.adapter.in.web.dto.CreateOrderRequest;
import br.com.joshua.apiflowservice.orders.adapter.in.web.dto.OrderResponse;
import br.com.joshua.apiflowservice.orders.application.port.in.CreateOrderUseCase;
import br.com.joshua.apiflowservice.orders.application.port.in.CreateOrderUseCase.CreateOrderCommand;
import br.com.joshua.apiflowservice.orders.application.port.in.CreateOrderUseCase.Line;
import br.com.joshua.apiflowservice.orders.application.port.in.GetOrderQuery;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final CreateOrderUseCase createOrder;
    private final GetOrderQuery getOrder;

    public OrderController(CreateOrderUseCase createOrder, GetOrderQuery getOrder) {
        this.createOrder = createOrder;
        this.getOrder = getOrder;
    }

    @PostMapping
    @FlowTrack(type = ComponentType.CONTROLLER, component = "OrderController")
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        CreateOrderCommand command = new CreateOrderCommand(request.items().stream()
                .map(item -> new Line(item.productId(), item.quantity()))
                .toList());
        OrderResponse created = OrderResponse.from(createOrder.execute(command));
        return ResponseEntity.created(URI.create("/api/orders/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    @FlowTrack(type = ComponentType.CONTROLLER, component = "OrderController")
    public OrderResponse getOrder(@PathVariable Long id) {
        return OrderResponse.from(getOrder.getOrder(id));
    }

    @GetMapping
    @FlowTrack(type = ComponentType.CONTROLLER, component = "OrderController")
    public List<OrderResponse> recentOrders(@RequestParam(defaultValue = "20") int limit) {
        return getOrder.recentOrders(limit).stream().map(OrderResponse::from).toList();
    }
}
