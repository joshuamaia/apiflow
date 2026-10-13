package br.com.joshua.apiflowservice.orders.application.port.in;

import br.com.joshua.apiflowservice.orders.domain.Order;

import java.util.List;

public interface GetOrderQuery {

    Order getOrder(Long id);

    List<Order> recentOrders(int limit);
}
