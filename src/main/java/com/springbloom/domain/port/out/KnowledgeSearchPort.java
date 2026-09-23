package com.springbloom.domain.port.out;

import java.util.List;

/**
 * Retrieval over the shop's written knowledge: the corporate manual, the two
 * market reports and the botany textbook.
 *
 * The scope is part of the query rather than a detail of the adapter because it
 * is a business rule, not a tuning knob: the textbook is most of the corpus by
 * volume, and searched alongside the manual it would win top-k on any
 * botanical wording and push the policy that answers the question out.
 */
public interface KnowledgeSearchPort {

    List<KnowledgePassage> search(KnowledgeQuery query);

    /** Which half of the corpus a question is allowed to reach. */
    enum Scope {  // I want my agent to know about the corpus, not the adapter.

        /** The manual and the two market reports: what the shop does and promises. */
        COMMERCIAL,

        /** The botany textbook: how a plant works. */
        BOTANICAL
    }

    record KnowledgeQuery(String question, Scope scope, int maxResults) {

        public KnowledgeQuery {
            if (question == null || question.isBlank()) {
                throw new IllegalArgumentException("question must not be blank");
            }
            if (scope == null) {
                throw new IllegalArgumentException("scope is required");
            }
            if (maxResults < 1) {
                throw new IllegalArgumentException("maxResults must be positive");
            }
        }

        public static KnowledgeQuery of(String question, Scope scope) {
            return new KnowledgeQuery(question, scope, 4);
        }
    }

    /**
     * A passage as retrieved. documentTitle and section exist so the agent can
     * say where an answer came from instead of presenting it as its own.
     */
    record KnowledgePassage(String content, 
        String documentTitle,
         String section, 
         double score) {
    }
}
