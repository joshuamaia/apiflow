package br.com.joshua.apiflowservice.flowtracking.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface TraceSpanJpaRepository extends JpaRepository<TraceSpanEntity, Long> {
}
