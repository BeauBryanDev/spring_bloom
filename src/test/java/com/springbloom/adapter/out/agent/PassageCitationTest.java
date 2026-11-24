package com.springbloom.adapter.out.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.springbloom.adapter.out.agent.FlorabelleTools.Passage;
import com.springbloom.adapter.out.knowledge.KnowledgeSource;
import com.springbloom.domain.port.out.KnowledgeSearchPort.KnowledgePassage;

class PassageCitationTest {

    @Test
    void manualCitesDocumentAndSection() {
        Passage passage = Passage.of(new KnowledgePassage("texto",
                "Manual corporativo y de operaciones de Spring-Bloom",
                "11. Politica de Reembolso", 0.6));

        assertThat(passage.cite())
                .isEqualTo("Manual corporativo y de operaciones de Spring-Bloom, 11. Politica de Reembolso");
        assertThat(passage.text()).isEqualTo("texto");
    }

    @Test
    void documentWithoutSectionCitesTitleOnly() {
        assertThat(Passage.citation("El mercado mundial de las flores", "")).isEqualTo("El mercado mundial de las flores");
        assertThat(Passage.citation("El mercado mundial de las flores", null)).isEqualTo("El mercado mundial de las flores");
    }

    @Test
    void missingTitleNeverLeavesADanglingComma() {
        assertThat(Passage.citation("", "11. Politica")).isEqualTo("11. Politica");
        assertThat(Passage.citation(null, null)).isEmpty();
    }

    @Test
    void figureCaptionsCiteTheBookTheyBelongTo() {
        assertThat(KnowledgeSource.BOTANY_FIGURES.citationTitle())
                .isEqualTo(KnowledgeSource.BOTANY_BOOK.title());
        assertThat(KnowledgeSource.SPRING_BLOOM_POLICY.citationTitle())
                .isEqualTo(KnowledgeSource.SPRING_BLOOM_POLICY.title());
    }

    @Test
    void sourcesResolveByTheKeyStoredInChunkMetadata() {
        assertThat(KnowledgeSource.byKey("botany_figures")).contains(KnowledgeSource.BOTANY_FIGURES);
        assertThat(KnowledgeSource.byKey("unknown")).isEmpty();
        assertThat(KnowledgeSource.byKey(null)).isEmpty();
    }
}
