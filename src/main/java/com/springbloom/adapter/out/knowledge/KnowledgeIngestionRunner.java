package com.springbloom.adapter.out.knowledge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Embeds the extracted knowledge files into pgvector.
 *
 * Behind the "ingest" profile so it can never fire on a normal boot or under
 * test: embedding costs money, and devtools restarts this app whenever a
 * resource changes. Each file is hashed and skipped if unchanged, so running it
 * twice is free.
 *
 * Run with: SPRING_PROFILES_ACTIVE=ingest ./run.sh
 */
@Component
@Profile("ingest")
public class KnowledgeIngestionRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIngestionRunner.class);

    /** Chunks per embedding call. Small enough to stay well inside the request limit. */
    private static final int BATCH_SIZE = 50;

    private final VectorStore vectorStore;
    private final KnowledgeDocumentRegistry registry;

    public KnowledgeIngestionRunner(VectorStore vectorStore, KnowledgeDocumentRegistry registry) {
        this.vectorStore = vectorStore;
        this.registry = registry;
    }

    @Override
    public void run(String... args) {
        boolean force = List.of(args).contains("--force-reingest");
        int embedded = 0;

        for (KnowledgeSource source : KnowledgeSource.values()) {
            String text = read(source);
            String checksum = sha256(text);
            Optional<String> known = registry.checksumOf(source);

            if (!force && known.filter(checksum::equals).isPresent()) {
                log.info("knowledge: {} unchanged, skipping", source.key());
                continue;
            }

            List<KnowledgeChunk> chunks = KnowledgeChunker.chunk(source, text);
            if (chunks.isEmpty()) {
                log.warn("knowledge: {} produced no chunks, skipping", source.key());
                continue;
            }

            // A changed file is replaced whole. Re-embedding a source without
            // clearing it first would leave the old chunks alongside the new.
            if (known.isPresent()) {
                vectorStore.delete("source == '" + source.key() + "'");
            }

            ingest(source, chunks);
            registry.record(source, checksum, chunks.size());
            embedded += chunks.size();
            log.info("knowledge: {} embedded, {} chunks", source.key(), chunks.size());
        }

        log.info("knowledge: ingestion finished, {} chunks embedded this run", embedded);
    }

    private void ingest(KnowledgeSource source, List<KnowledgeChunk> chunks) {
        List<Document> batch = new ArrayList<>(BATCH_SIZE);
        for (KnowledgeChunk chunk : chunks) {
            batch.add(new Document(chunk.content(), chunk.metadata()));
            if (batch.size() == BATCH_SIZE) {
                vectorStore.add(batch);
                batch = new ArrayList<>(BATCH_SIZE);
            }
        }
        if (!batch.isEmpty()) {
            vectorStore.add(batch);
        }
    }

    private String read(KnowledgeSource source) {
        try {
            return new ClassPathResource(source.resourcePath())
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "missing knowledge file " + source.resourcePath()
                            + "; run scripts/extract_knowledge.py", e);
        }
    }

    static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
