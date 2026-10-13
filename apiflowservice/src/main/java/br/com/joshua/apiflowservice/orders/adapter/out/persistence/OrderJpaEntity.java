package br.com.joshua.apiflowservice.orders.adapter.out.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
class OrderJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal total;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<OrderItemJpaEntity> items = new ArrayList<>();

    protected OrderJpaEntity() {
    }

    OrderJpaEntity(Instant createdAt, String status, BigDecimal total) {
        this.createdAt = createdAt;
        this.status = status;
        this.total = total;
    }

    void addItem(OrderItemJpaEntity item) {
        item.attachTo(this);
        items.add(item);
    }

    Long getId() {
        return id;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    String getStatus() {
        return status;
    }

    BigDecimal getTotal() {
        return total;
    }

    List<OrderItemJpaEntity> getItems() {
        return items;
    }
}
