package com.springbloom.adapter.out.persistence.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.springbloom.adapter.out.persistence.entity.GuardrailEventEntity;
import com.springbloom.domain.model.GuardrailEvent;
import com.springbloom.domain.model.GuardrailEvent.Stage;
import com.springbloom.domain.port.out.GuardrailEventRepository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GuardrailEventRepositoryAdapter implements GuardrailEventRepository {

    private final GuardrailEventJpaRepository jpaRepository;

    @Override
    @Transactional
    public void record(UUID conversationId, Stage stage, String rule) {
        GuardrailEventEntity entity = new GuardrailEventEntity();
        entity.setConversationId(conversationId);
        entity.setStage(stage.name());
        entity.setRule(rule);
        jpaRepository.saveAndFlush(entity);
    }

    @Override
    public List<GuardrailEvent> summarySince(Instant from) {
        return jpaRepository.summarySince(from).stream()
                .map(row -> new GuardrailEvent(
                        Stage.valueOf((String) row[0]),
                        (String) row[1],
                        ((Number) row[2]).longValue(),
                        toInstant(row[3])))
                .toList();
    }

    private static Instant toInstant(Object value) {
        return value instanceof Timestamp timestamp ? timestamp.toInstant() : (Instant) value;
    }
}
