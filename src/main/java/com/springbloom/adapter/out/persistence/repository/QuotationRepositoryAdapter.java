package com.springbloom.adapter.out.persistence.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.springbloom.adapter.out.persistence.entity.QuotationEntity;
import com.springbloom.adapter.out.persistence.mapper.QuotationMapper;
import com.springbloom.domain.model.Quotation;
import com.springbloom.domain.model.QuotationStatus;
import com.springbloom.domain.model.QuotationSummary;
import com.springbloom.domain.model.vo.Money;
import com.springbloom.domain.port.out.QuotationRepository;
import com.springbloom.domain.service.DocumentNumberGenerator;

/**
 * Persists the quotation aggregate. The 5-digit suffix collides often enough
 * that a rejected number is routine, so the insert is attempted with a fresh
 * number until the UNIQUE constraint accepts one.
 */
@Repository
public class QuotationRepositoryAdapter implements QuotationRepository {

    private static final Logger log = LoggerFactory.getLogger(QuotationRepositoryAdapter.class);
    private static final int MAX_ATTEMPTS = 3;

    private final QuotationJpaRepository jpaRepository;
    private final DocumentNumberGenerator numberGenerator;
    private final TransactionTemplate attemptTransaction;

    public QuotationRepositoryAdapter(
            QuotationJpaRepository jpaRepository,
            @Qualifier("quotationNumberGenerator") DocumentNumberGenerator numberGenerator,
            PlatformTransactionManager transactionManager) {

        this.jpaRepository = jpaRepository;
        this.numberGenerator = numberGenerator;
        this.attemptTransaction = new TransactionTemplate(transactionManager);
        this.attemptTransaction.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public Quotation save(Quotation quotation) {
        if (quotation.persisted()) {
            return attemptTransaction.execute(status -> insert(quotation));
        }

        DataIntegrityViolationException lastFailure = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            Quotation numbered = quotation.withNumber(numberGenerator.next());
            try {
                return attemptTransaction.execute(status -> insert(numbered));
            } catch (DataIntegrityViolationException failure) {
                if (!isNumberCollision(failure)) {
                    throw failure;
                }
                lastFailure = failure;
                log.info("Quotation number {} was taken, retrying ({}/{})",
                        numbered.quotationNumber(), attempt, MAX_ATTEMPTS);
            }
        }

        throw new IllegalStateException(
                "Could not allocate a free quotation number in " + MAX_ATTEMPTS + " attempts",
                lastFailure);
    }

    /**
     * Runs inside one attempt's transaction. saveAndFlush is what makes the
     * constraint fire here rather than at an unrelated commit later.
     */
    private Quotation insert(Quotation quotation) {
        QuotationEntity saved = jpaRepository.saveAndFlush(QuotationMapper.toEntity(quotation));
        return QuotationMapper.toDomain(saved);
    }

    /** Any other constraint is a real bug and must not be retried. */
    private boolean isNumberCollision(DataIntegrityViolationException failure) {
        String message = failure.getMostSpecificCause().getMessage();
        return message != null && message.contains("quotation_number");
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Quotation> findByNumber(String quotationNumber) {
        return jpaRepository.findByQuotationNumber(quotationNumber)
                .map(QuotationMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Quotation> findById(Long id) {
        return jpaRepository.findWithLinesById(id)
                .map(QuotationMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Quotation> findByConversationId(UUID conversationId) {
        return jpaRepository.findByConversationIdOrderByCreatedAtDesc(conversationId).stream()
                .map(QuotationMapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuotationSummary> findAllSummaries() {
        return jpaRepository.findAllSummaryRows().stream()
                .map(row -> new QuotationSummary(
                        (String) row[0],
                        (QuotationStatus) row[1],
                        Money.of((BigDecimal) row[2]),
                        (Instant) row[3]))
                .toList();
    }
}
