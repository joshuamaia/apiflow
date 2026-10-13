package br.com.joshua.apiflowservice.flowtracking.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

interface TraceExecutionJpaRepository extends JpaRepository<TraceExecutionEntity, Long> {

    /** Spans are removed by the {@code ON DELETE CASCADE} foreign key. */
    @Modifying
    @Query(value = """
            DELETE FROM trace_execution
             WHERE started_at < :cutoff
                OR id NOT IN (SELECT id FROM trace_execution ORDER BY started_at DESC LIMIT :maxTraces)
            """, nativeQuery = true)
    int deleteBeyondRetention(@Param("maxTraces") int maxTraces, @Param("cutoff") Instant cutoff);

    @Modifying
    @Query(value = "DELETE FROM trace_execution", nativeQuery = true)
    int deleteAllTraces();
}
