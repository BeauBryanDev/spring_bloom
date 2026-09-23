package com.springbloom.adapter.out.persistence.repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.springbloom.domain.model.FlowerOrderSummary;
import com.springbloom.domain.model.OrderStatus;
import com.springbloom.domain.model.vo.Money;
import com.springbloom.domain.port.out.FlowerOrderRepository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FlowerOrderRepositoryAdapter implements FlowerOrderRepository {

    private final FlowerOrderJpaRepository jpaRepository;

    @Override
    public List<FlowerOrderSummary> findAllSummaries() {
        return jpaRepository.findAllSummaryRows().stream()
                .map(row -> new FlowerOrderSummary(
                        (String) row[0],
                        (String) row[1],
                        OrderStatus.valueOf((String) row[2]),
                        Money.of((BigDecimal) row[3]),
                        toInstant(row[4])))
                .toList();
    }

    /** The native driver may hand created_at back as Timestamp, Instant or OffsetDateTime. */
    private static Instant toInstant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        throw new IllegalStateException("Unexpected timestamp type: " + value.getClass());
    }
}
