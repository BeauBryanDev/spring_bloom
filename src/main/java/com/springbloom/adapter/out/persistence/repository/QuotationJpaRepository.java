package com.springbloom.adapter.out.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.springbloom.adapter.out.persistence.entity.QuotationEntity;

public interface QuotationJpaRepository extends JpaRepository<QuotationEntity, Long> {

    /**
     * Header fields only, no items - the dashboard aggregates totals and status,
     * never a line. Avoids loading the whole aggregate for every document.
     */
    @Query("SELECT q.quotationNumber, q.status, q.totalAmount, q.createdAt "
            + "FROM QuotationEntity q ORDER BY q.createdAt DESC")
    List<Object[]> findAllSummaryRows();

    /**
     * The graph joins the lines only. Adding items.species would fetch two bags
     * in one select, which Hibernate refuses; the species arrive instead in one
     * batched query thanks to @BatchSize on that collection.
     */
    @EntityGraph(attributePaths = "items")
    Optional<QuotationEntity> findByQuotationNumber(String quotationNumber);

    @EntityGraph(attributePaths = "items")
    List<QuotationEntity> findByConversationIdOrderByCreatedAtDesc(UUID conversationId);

    /** The inherited findById ignores entity graphs, so the lookup is declared here. */
    @EntityGraph(attributePaths = "items")
    Optional<QuotationEntity> findWithLinesById(Long id);
}
