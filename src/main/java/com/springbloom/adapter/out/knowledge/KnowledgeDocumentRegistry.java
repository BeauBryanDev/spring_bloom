package com.springbloom.adapter.out.knowledge;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Bookkeeping over knowledge_document: which source files have been embedded
 * and at what checksum. This is what makes re-running ingestion free.
 *
 * Plain JDBC rather than an entity and a port: there is no domain concept here
 * and nothing outside ingestion reads this table, so a mapper and a repository
 * interface would be scaffolding around one insert.
 */
@Component
public class KnowledgeDocumentRegistry {

    private final JdbcTemplate jdbc;

    public KnowledgeDocumentRegistry(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<String> checksumOf(KnowledgeSource source) {
        List<String> found = jdbc.queryForList(
                "SELECT checksum FROM knowledge_document WHERE source = ?", String.class, source.key());
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    /** Written only after the chunks are, so an interrupted run is not recorded as done. */
    public void record(KnowledgeSource source, String checksum, int chunkCount) {
        jdbc.update("""
                INSERT INTO knowledge_document (source, title, checksum, chunk_count, ingested_at)
                VALUES (?, ?, ?, ?, NOW())
                ON CONFLICT (source) DO UPDATE
                SET title = EXCLUDED.title,
                    checksum = EXCLUDED.checksum,
                    chunk_count = EXCLUDED.chunk_count,
                    ingested_at = NOW()
                """, source.key(), source.title(), checksum, chunkCount);
    }
}
