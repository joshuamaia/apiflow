package br.com.joshua.apiflowservice.orders.application;

import br.com.joshua.apiflowservice.flowtracking.api.FlowTrack;
import br.com.joshua.apiflowservice.flowtracking.domain.ComponentType;
import br.com.joshua.apiflowservice.orders.application.port.in.GetOrderQuery;
import br.com.joshua.apiflowservice.orders.application.port.out.OrderRepository;
import br.com.joshua.apiflowservice.orders.domain.Order;
import br.com.joshua.apiflowservice.orders.domain.OrderNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OrderQueryService implements GetOrderQuery {

    private final OrderRepository orderRepository;

    public OrderQueryService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    @Transactional(readOnly = true)
    @FlowTrack(type = ComponentType.USE_CASE, component = "GetOrderQuery")
    public Order getOrder(Long id) {
        return orderRepository.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
    }

    @Override
    @Transactional(readOnly = true)
    @FlowTrack(type = ComponentType.USE_CASE, component = "GetOrderQuery")
    public List<Order> recentOrders(int limit) {
        return orderRepository.findRecent(Math.clamp(limit, 1, 100));
    }
}
