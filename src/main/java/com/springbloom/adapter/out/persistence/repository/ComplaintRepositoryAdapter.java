package com.springbloom.adapter.out.persistence.repository;

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

import com.springbloom.adapter.out.persistence.entity.ComplaintEntity;
import com.springbloom.adapter.out.persistence.mapper.ComplaintMapper;
import com.springbloom.domain.model.Complaint;
import com.springbloom.domain.port.out.ComplaintRepository;
import com.springbloom.domain.service.DocumentNumberGenerator;

/**
 * Same shape as QuotationRepositoryAdapter, and for the same reason: the 5-digit
 * suffix collides often enough that a rejected number is routine.
 */
@Repository
public class ComplaintRepositoryAdapter implements ComplaintRepository {

    private static final Logger log = LoggerFactory.getLogger(ComplaintRepositoryAdapter.class);
    private static final int MAX_ATTEMPTS = 3;

    private final ComplaintJpaRepository jpaRepository;
    private final DocumentNumberGenerator numberGenerator;
    private final TransactionTemplate attemptTransaction;

    public ComplaintRepositoryAdapter(
            ComplaintJpaRepository jpaRepository,
            @Qualifier("complaintNumberGenerator") DocumentNumberGenerator numberGenerator,
            PlatformTransactionManager transactionManager) {

        this.jpaRepository = jpaRepository;
        this.numberGenerator = numberGenerator;
        this.attemptTransaction = new TransactionTemplate(transactionManager);
        this.attemptTransaction.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public Complaint save(Complaint complaint) {
        if (complaint.persisted()) {
            return attemptTransaction.execute(status -> insert(complaint));
        }

        DataIntegrityViolationException lastFailure = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            Complaint numbered = complaint.withNumber(numberGenerator.next());
            try {
                return attemptTransaction.execute(status -> insert(numbered));
            } catch (DataIntegrityViolationException failure) {
                if (!isNumberCollision(failure)) {
                    throw failure;
                }
                lastFailure = failure;
                log.info("Complaint number {} was taken, retrying ({}/{})",
                        numbered.complaintNumber(), attempt, MAX_ATTEMPTS);
            }
        }

        throw new IllegalStateException(
                "Could not allocate a free complaint number in " + MAX_ATTEMPTS + " attempts",
                lastFailure);
    }

    /** saveAndFlush, so the constraint fires inside the try rather than at commit. */
    private Complaint insert(Complaint complaint) {
        ComplaintEntity saved = jpaRepository.saveAndFlush(ComplaintMapper.toEntity(complaint));
        return ComplaintMapper.toDomain(saved);
    }

    /** Any other constraint is a real bug and must not burn a retry. */
    private boolean isNumberCollision(DataIntegrityViolationException failure) {
        String message = failure.getMostSpecificCause().getMessage();
        return message != null && message.contains("complaint_number");
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Complaint> findByNumber(String complaintNumber) {
        return jpaRepository.findByComplaintNumber(complaintNumber).map(ComplaintMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Complaint> findById(Long id) {
        return jpaRepository.findById(id).map(ComplaintMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Complaint> findByOrderId(Long orderId) {
        return jpaRepository.findByOrderIdOrderByCreatedAtDesc(orderId).stream()
                .map(ComplaintMapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Complaint> findByConversationId(UUID conversationId) {
        return jpaRepository.findByConversationIdOrderByCreatedAtDesc(conversationId).stream()
                .map(ComplaintMapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Long> findOrderIdByNumber(String orderNumber) {
        return jpaRepository.findOrderIdByNumber(orderNumber);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Complaint> findAll() {
        return jpaRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(ComplaintMapper::toDomain)
                .toList();
    }
}
