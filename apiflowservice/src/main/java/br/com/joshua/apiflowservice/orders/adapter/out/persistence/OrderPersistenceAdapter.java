package br.com.joshua.apiflowservice.orders.adapter.out.persistence;

import br.com.joshua.apiflowservice.flowtracking.api.FlowTrack;
import br.com.joshua.apiflowservice.flowtracking.api.FlowTracker;
import br.com.joshua.apiflowservice.flowtracking.domain.ComponentType;
import br.com.joshua.apiflowservice.orders.application.port.out.OrderRepository;
import br.com.joshua.apiflowservice.orders.domain.Order;
import br.com.joshua.apiflowservice.orders.domain.OrderItem;
import br.com.joshua.apiflowservice.orders.domain.OrderStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * JPA implementation of {@link OrderRepository}. {@code saveAndFlush} forces the INSERT statements to
 * run inside the explicit DATABASE span instead of at commit time.
 */
@Component
class OrderPersistenceAdapter implements OrderRepository {

    private static final String DATABASE = "PostgreSQL";

    private final OrderJpaRepository jpa;
    private final FlowTracker tracker;

    OrderPersistenceAdapter(OrderJpaRepository jpa, FlowTracker tracker) {
        this.jpa = jpa;
        this.tracker = tracker;
    }

    @Override
    @FlowTrack(type = ComponentType.REPOSITORY, component = "OrderRepository")
    public Order save(Order order) {
        OrderJpaEntity entity = new OrderJpaEntity(order.createdAt(), order.status().name(), order.total());
        order.items().forEach(item ->
                entity.addItem(new OrderItemJpaEntity(item.productId(), item.quantity(), item.unitPrice())));
        OrderJpaEntity saved = tracker.track(DATABASE, "INSERT orders, order_items", ComponentType.DATABASE,
                () -> jpa.saveAndFlush(entity));
        return toDomain(saved);
    }

    @Override
    @FlowTrack(type = ComponentType.REPOSITORY, component = "OrderRepository")
    public Optional<Order> findById(Long id) {
        return tracker.track(DATABASE, "SELECT orders by id", ComponentType.DATABASE,
                () -> jpa.findWithItemsById(id)).map(OrderPersistenceAdapter::toDomain);
    }

    @Override
    @FlowTrack(type = ComponentType.REPOSITORY, component = "OrderRepository")
    public List<Order> findRecent(int limit) {
        return tracker.track(DATABASE, "SELECT recent orders", ComponentType.DATABASE,
                        () -> jpa.findAllByOrderByCreatedAtDesc(PageRequest.of(0, limit)))
                .stream().map(OrderPersistenceAdapter::toDomain).toList();
    }

    private static Order toDomain(OrderJpaEntity entity) {
        List<OrderItem> items = entity.getItems().stream()
                .map(i -> new OrderItem(i.getId(), i.getProductId(), i.getQuantity(), i.getUnitPrice()))
                .toList();
        return new Order(entity.getId(), entity.getCreatedAt(), OrderStatus.valueOf(entity.getStatus()),
                entity.getTotal(), items);
    }
}
