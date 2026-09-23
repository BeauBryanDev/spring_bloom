package com.springbloom.adapter.out.knowledge;

import java.util.Set;

/**
 * The four documents, and the vocabulary the retrieval filters use. The book is
 * 85% of the corpus by volume, so it is never searched alongside the policy: it
 * would win top-k on any botanical wording and push the manual out.
 */
public enum KnowledgeSource {

    SPRING_BLOOM_POLICY("spring_bloom_policy", "Manual corporativo y de operaciones de Spring-Bloom",
            "knowledge/spring_bloom_policy.txt"),
    COLOMBIA_MARKET("colombia_market", "El mercado de las flores en Colombia",
            "knowledge/colombia_market.txt"),
    WORLD_MARKET("world_market", "El mercado mundial de las flores",
            "knowledge/world_market.txt"),
    BOTANY_BOOK("botany_book", "Botanica: generalidades, morfologia y anatomia de plantas superiores",
            "knowledge/botany_book.txt"),
    BOTANY_FIGURES("botany_figures", "Botanica: leyendas de figuras",
            "knowledge/botany_figures.txt");

    /** What searchPolicy retrieves from: the commercial half of the corpus. */
    public static final Set<KnowledgeSource> COMMERCIAL =
            Set.of(SPRING_BLOOM_POLICY, COLOMBIA_MARKET, WORLD_MARKET);

    /** What explainBotany retrieves from. */
    public static final Set<KnowledgeSource> BOTANICAL =
            Set.of(BOTANY_BOOK, BOTANY_FIGURES);

    private final String key;
    private final String title;
    private final String resourcePath;

    KnowledgeSource(String key, String title, String resourcePath) {
        this.key = key;
        this.title = title;
        this.resourcePath = resourcePath;
    }

    public String key() {
        return key;
    }

    public String title() {
        return title;
    }

    public String resourcePath() {
        return resourcePath;
    }
}
