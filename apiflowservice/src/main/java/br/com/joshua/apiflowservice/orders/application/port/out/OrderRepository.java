package br.com.joshua.apiflowservice.orders.application.port.out;

import br.com.joshua.apiflowservice.orders.domain.Order;

import java.util.List;
import java.util.Optional;

public interface OrderRepository {

    Order save(Order order);

    Optional<Order> findById(Long id);

    List<Order> findRecent(int limit);
}
