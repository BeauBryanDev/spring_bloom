package com.springbloom.adapter.out.persistence.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.springbloom.adapter.out.persistence.entity.FlowerOrderEntity;

public interface FlowerOrderJpaRepository extends JpaRepository<FlowerOrderEntity, Long> {

    /**
     * Joins to customer for display only - flower_order itself carries no name,
     * and there is no CustomerEntity yet to join through JPQL, hence native SQL.
     */
    @Query(value = """
            SELECT o.order_number, c.full_name, o.status::text, o.total_amount, o.created_at
            FROM flower_order o
            JOIN customer c ON c.id = o.customer_id
            ORDER BY o.created_at DESC
            """, nativeQuery = true)
    List<Object[]> findAllSummaryRows();
}
