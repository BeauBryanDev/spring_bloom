package com.springbloom.adapter.out.persistence.repository;

import java.sql.Date;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.springbloom.adapter.out.persistence.entity.AgentUsageEntity;
import com.springbloom.domain.model.DailyUsage;
import com.springbloom.domain.model.TokenUsage;
import com.springbloom.domain.model.UsageTotals;
import com.springbloom.domain.port.out.AgentUsageRepository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgentUsageRepositoryAdapter implements AgentUsageRepository {

    private final AgentUsageJpaRepository jpaRepository;

    @Override
    @Transactional
    public void record(UUID conversationId, TokenUsage usage, boolean hadImage) {
        AgentUsageEntity entity = new AgentUsageEntity();
        entity.setConversationId(conversationId);
        entity.setModel(usage.model());
        entity.setInputTokens(usage.inputTokens());
        entity.setOutputTokens(usage.outputTokens());
        entity.setCacheReadTokens(usage.cacheReadTokens());
        entity.setCacheWriteTokens(usage.cacheWriteTokens());
        entity.setHadImage(hadImage);
        jpaRepository.saveAndFlush(entity);
    }

    @Override
    public UsageTotals totalsSince(Instant from) {
        List<Object[]> rows = jpaRepository.totalsSince(from);
        return rows.isEmpty() ? UsageTotals.NONE : totals(rows.get(0), 0);
    }

    @Override
    public List<DailyUsage> dailySince(Instant from) {
        return jpaRepository.dailySince(from).stream()
                .map(row -> new DailyUsage(((Date) row[0]).toLocalDate(), totals(row, 1)))
                .toList();
    }

    private static UsageTotals totals(Object[] row, int offset) {
        return new UsageTotals(
                ((Number) row[offset]).longValue(),
                ((Number) row[offset + 1]).longValue(),
                ((Number) row[offset + 2]).longValue(),
                ((Number) row[offset + 3]).longValue(),
                ((Number) row[offset + 4]).longValue());
    }
}
