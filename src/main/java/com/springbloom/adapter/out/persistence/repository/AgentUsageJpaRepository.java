package com.springbloom.adapter.out.persistence.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.springbloom.adapter.out.persistence.entity.AgentUsageEntity;

public interface AgentUsageJpaRepository extends JpaRepository<AgentUsageEntity, Long> {

    @Query(value = """
            SELECT COUNT(*), COALESCE(SUM(input_tokens), 0), 
                            COALESCE(SUM(output_tokens), 0),
                   COALESCE(SUM(cache_read_tokens), 0), 
                   COALESCE(SUM(cache_write_tokens), 0)
            FROM agent_usage
            WHERE (CAST(:from AS timestamptz) IS NULL OR created_at >= :from)
            """, nativeQuery = true)
    List<Object[]> totalsSince(@Param("from") Instant from);

    @Query(value = """
            SELECT (created_at AT TIME ZONE 'America/Bogota')::date AS day,
                   COUNT(*), SUM(input_tokens), SUM(output_tokens),
                   SUM(cache_read_tokens), SUM(cache_write_tokens)
            FROM agent_usage
            WHERE created_at >= :from
            GROUP BY 1
            ORDER BY 1
            """, nativeQuery = true)
    List<Object[]> dailySince(@Param("from") Instant from);
}
