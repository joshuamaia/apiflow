package br.com.joshua.apiflowservice.orders.application;

import br.com.joshua.apiflowservice.orders.application.port.in.CreateOrderUseCase.CreateOrderCommand;
import br.com.joshua.apiflowservice.orders.application.port.in.CreateOrderUseCase.Line;
import br.com.joshua.apiflowservice.orders.application.port.out.OrderRepository;
import br.com.joshua.apiflowservice.orders.domain.Order;
import br.com.joshua.apiflowservice.products.application.ProductService;
import br.com.joshua.apiflowservice.products.application.StockService;
import br.com.joshua.apiflowservice.products.domain.InsufficientStockException;
import br.com.joshua.apiflowservice.products.domain.Product;
import br.com.joshua.apiflowservice.products.domain.ProductNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateOrderServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

    @Mock
    private ProductService productService;
    @Mock
    private StockService stockService;
    @Mock
    private OrderRepository orderRepository;

    private CreateOrderService service;

    private final Product keyboard = new Product(1L, "Mechanical Keyboard", new BigDecimal("349.90"), 100);
    private final Product headset = new Product(6L, "Limited Edition Headset", new BigDecimal("999.00"), 3);

    @BeforeEach
    void setUp() {
        service = new CreateOrderService(productService, stockService, orderRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void loadsProductsReservesStockAndPersistsTheOrder() {
        when(productService.getProduct(1L)).thenReturn(keyboard);
        when(productService.getProduct(6L)).thenReturn(headset);
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Order order = service.execute(new CreateOrderCommand(List.of(new Line(1L, 2), new Line(6L, 1))));

        InOrder flow = inOrder(productService, stockService, orderRepository);
        flow.verify(productService).getProduct(1L);
        flow.verify(stockService).reserveStock(keyboard, 2);
        flow.verify(productService).getProduct(6L);
        flow.verify(stockService).reserveStock(headset, 1);
        flow.verify(orderRepository).save(any());

        assertThat(order.total()).isEqualByComparingTo("1698.80");
        assertThat(order.createdAt()).isEqualTo(NOW);
        assertThat(order.items()).hasSize(2);
    }

    @Test
    void doesNotPersistTheOrderWhenStockIsInsufficient() {
        when(productService.getProduct(6L)).thenReturn(headset);
        doThrow(new InsufficientStockException(6L, "Limited Edition Headset", 4, 3))
                .when(stockService).reserveStock(headset, 4);

        assertThatThrownBy(() -> service.execute(new CreateOrderCommand(List.of(new Line(6L, 4)))))
                .isInstanceOf(InsufficientStockException.class);

        verify(orderRepository, never()).save(any());
    }

    @Test
    void stopsWhenAProductDoesNotExist() {
        when(productService.getProduct(99L)).thenThrow(new ProductNotFoundException(99L));

        assertThatThrownBy(() -> service.execute(new CreateOrderCommand(List.of(new Line(99L, 1)))))
                .isInstanceOf(ProductNotFoundException.class);

        verify(stockService, never()).reserveStock(any(), org.mockito.ArgumentMatchers.anyInt());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void freezesUnitPriceAtOrderTime() {
        when(productService.getProduct(1L)).thenReturn(keyboard);
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);

        service.execute(new CreateOrderCommand(List.of(new Line(1L, 3))));

        verify(orderRepository).save(saved.capture());
        assertThat(saved.getValue().items().getFirst().unitPrice()).isEqualByComparingTo("349.90");
    }
}
