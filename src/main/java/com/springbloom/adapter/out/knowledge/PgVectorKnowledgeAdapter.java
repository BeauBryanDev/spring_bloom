package com.springbloom.adapter.out.knowledge;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.springbloom.domain.port.out.KnowledgeSearchPort;

/** Similarity search over pgvector, behind the domain's port. */
@Component
public class PgVectorKnowledgeAdapter implements KnowledgeSearchPort {

    private static final Logger log = LoggerFactory.getLogger(PgVectorKnowledgeAdapter.class);

    private final VectorStore vectorStore;

    /**
     * Below this a passage is not an answer to the question asked. Finding
     * nothing is a result the agent has to reply to, the same rule as an
     * unrecognized photo: returning the least-bad chunk instead would hand the
     * model an irrelevant policy to quote as though it were relevant.
     *
     * 0.30 was measured, not guessed. Against the ingested corpus the weakest
     * genuine hit scores 0.386 (quotation validity, whose section is long
     * enough to dilute its own embedding) and the strongest off-topic question
     * scores 0.243. Re-measure it if the documents change.
     */
    private final double similarityThreshold;

    public PgVectorKnowledgeAdapter(
            VectorStore vectorStore,
            @Value("${florabelle.knowledge.similarity-threshold:0.30}") double similarityThreshold) {

        this.vectorStore = vectorStore;
        this.similarityThreshold = similarityThreshold;
    }

    @Override
    public List<KnowledgePassage> search(KnowledgeQuery query) {
        SearchRequest request = SearchRequest.builder()
                .query(query.question())
                .topK(query.maxResults())
                .similarityThreshold(similarityThreshold)
                .filterExpression(filterFor(query.scope()))
                .build();

        List<Document> documents = vectorStore.similaritySearch(request);
        if (documents == null || documents.isEmpty()) {
            log.info("knowledge: nothing above {} for a {} question", 
            similarityThreshold, query.scope());
            return List.of();
        }

        return documents.stream().map(PgVectorKnowledgeAdapter::toPassage).toList();
    }

    /**
     * Restricts the search to one half of the corpus. Spring AI parses this
     * string into its own filter and runs it as metadata::jsonb @@ '...', which
     * is what ix_vector_store_metadata indexes.
     */
    private static String filterFor(Scope scope) {
        Set<KnowledgeSource> sources = switch (scope) {
            case COMMERCIAL -> KnowledgeSource.COMMERCIAL;
            case BOTANICAL -> KnowledgeSource.BOTANICAL;
        };

        String keys = sources.stream()
                .map(source -> "'" + source.key() + "'")
                .collect(Collectors.joining(", "));

        return "source in [" + keys + "]";
    }

    private static KnowledgePassage toPassage(Document document) {
        Object title = document.getMetadata().get("title");
        Object section = document.getMetadata().get("section");
        Double score = document.getScore();

        return new KnowledgePassage(
                Objects.toString(document.getText(), ""),
                Objects.toString(title, ""),
                Objects.toString(section, ""),
                score == null ? 0.0 : score);
    }
}
