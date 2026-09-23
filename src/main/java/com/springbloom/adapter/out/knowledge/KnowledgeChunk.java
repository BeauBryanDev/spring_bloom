package com.springbloom.adapter.out.knowledge;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One unit of retrievable text. The metadata is what the two tools filter on:
 * searchPolicy excludes the book, explainBotany asks for nothing else.
 */
public record KnowledgeChunk(String content, KnowledgeSource source, String section, ChunkKind kind) {

    public KnowledgeChunk {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("chunk content must not be blank");
        }
        if (source == null) {
            throw new IllegalArgumentException("chunk source is required");
        }
        section = section == null ? "" : section.trim();
        kind = kind == null ? ChunkKind.PROSE : kind;
    }

    public static KnowledgeChunk prose(KnowledgeSource source, String section, String content) {
        return new KnowledgeChunk(content, source, section, ChunkKind.PROSE);
    }

    /** Flat map, because PgVectorStore serializes document metadata to JSON. */
    public Map<String, Object> metadata() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", source.key());
        metadata.put("title", source.title());
        metadata.put("section", section);
        metadata.put("kind", kind.name());
        return metadata;
    }
}
