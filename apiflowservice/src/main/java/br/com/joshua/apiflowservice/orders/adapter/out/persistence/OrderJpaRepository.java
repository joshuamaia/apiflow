package br.com.joshua.apiflowservice.orders.adapter.out.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface OrderJpaRepository extends JpaRepository<OrderJpaEntity, Long> {

    @EntityGraph(attributePaths = "items")
    Optional<OrderJpaEntity> findWithItemsById(Long id);

    @EntityGraph(attributePaths = "items")
    List<OrderJpaEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
