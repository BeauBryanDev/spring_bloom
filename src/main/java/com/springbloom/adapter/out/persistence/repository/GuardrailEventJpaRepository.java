package com.springbloom.adapter.out.persistence.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.springbloom.adapter.out.persistence.entity.GuardrailEventEntity;

public interface GuardrailEventJpaRepository extends JpaRepository<GuardrailEventEntity, Long> {

    @Query(value = """
            SELECT stage, rule, COUNT(*), MAX(created_at)
            FROM guardrail_event
            WHERE (CAST(:from AS timestamptz) IS NULL OR created_at >= :from)
            GROUP BY stage, rule
            ORDER BY COUNT(*) DESC, stage, rule
            """, nativeQuery = true)
    List<Object[]> summarySince(@Param("from") Instant from);
}
