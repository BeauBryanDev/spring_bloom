package com.springbloom.adapter.out.persistence.repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.springbloom.adapter.out.persistence.entity.FlowerOrderEntity;
import com.springbloom.adapter.out.persistence.mapper.FlowerOrderMapper;
import com.springbloom.domain.model.FlowerOrder;
import com.springbloom.domain.model.FlowerOrderSummary;
import com.springbloom.domain.model.OrderStatus;
import com.springbloom.domain.model.vo.Money;
import com.springbloom.domain.port.out.FlowerOrderRepository;
import com.springbloom.domain.service.DocumentNumberGenerator;

/**
 * Same shape as the quotation adapter: the order number's 5-digit suffix
 * collides routinely, so each insert attempt runs in its own transaction with a
 * fresh number until the UNIQUE constraint accepts one.
 */
@Repository
public class FlowerOrderRepositoryAdapter implements FlowerOrderRepository {

    private static final Logger log = LoggerFactory.getLogger(FlowerOrderRepositoryAdapter.class);
    private static final int MAX_ATTEMPTS = 3;

    private final FlowerOrderJpaRepository jpaRepository;
    private final DocumentNumberGenerator numberGenerator;
    private final TransactionTemplate attemptTransaction;

    public FlowerOrderRepositoryAdapter(
            FlowerOrderJpaRepository jpaRepository,
            @Qualifier("orderNumberGenerator") DocumentNumberGenerator numberGenerator,
            PlatformTransactionManager transactionManager) {

        this.jpaRepository = jpaRepository;
        this.numberGenerator = numberGenerator;
        this.attemptTransaction = new TransactionTemplate(transactionManager);
        this.attemptTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public FlowerOrder save(FlowerOrder order) {
        DataIntegrityViolationException lastFailure = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            FlowerOrder numbered = order.withNumber(numberGenerator.next());
            try {
                return attemptTransaction.execute(status -> insert(numbered));
            } catch (DataIntegrityViolationException failure) {
                if (!isNumberCollision(failure)) {
                    throw failure;
                }
                lastFailure = failure;
                log.info("Order number {} was taken, retrying ({}/{})",
                        numbered.orderNumber(), attempt, MAX_ATTEMPTS);
            }
        }
        throw new IllegalStateException(
                "Could not allocate a free order number in " + MAX_ATTEMPTS + " attempts", lastFailure);
    }

    private FlowerOrder insert(FlowerOrder order) {
        return FlowerOrderMapper.toDomain(jpaRepository.saveAndFlush(FlowerOrderMapper.toEntity(order)));
    }

    private boolean isNumberCollision(DataIntegrityViolationException failure) {
        String message = failure.getMostSpecificCause().getMessage();
        return message != null && message.contains("order_number");
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<FlowerOrder> findByNumber(String orderNumber) {
        return jpaRepository.findByOrderNumber(orderNumber).map(FlowerOrderMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<FlowerOrder> findById(Long id) {
        return jpaRepository.findWithLinesById(id).map(FlowerOrderMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<FlowerOrder> findByQuotationId(Long quotationId) {
        return jpaRepository.findByQuotationId(quotationId).map(FlowerOrderMapper::toDomain);
    }

    /** Loads and mutates in place so the updated_at trigger fires on a real UPDATE. */
    @Override
    @Transactional
    public void updateStatus(Long orderId, OrderStatus status) {
        FlowerOrderEntity entity = jpaRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("No order with id " + orderId));
        entity.setStatus(status);
        jpaRepository.saveAndFlush(entity);
    }

    @Override
    @Transactional(readOnly = true)
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
