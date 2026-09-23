package com.springbloom.adapter.out.knowledge;

import com.springbloom.domain.port.out.KnowledgeSearchPort;
import com.springbloom.domain.port.out.KnowledgeSearchPort.KnowledgePassage;
import com.springbloom.domain.port.out.KnowledgeSearchPort.KnowledgeQuery;
import com.springbloom.domain.port.out.KnowledgeSearchPort.Scope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Requires V3 applied and ingestion already run.
 *
 *   set -a; . ./.env; set +a; ./mvnw test
 *
 * This is the one test that calls an external model: embedding the query is
 * unavoidable if the filter and the threshold are to be proven against real
 * vectors. It is cheap and near-deterministic, unlike a chat completion - the
 * rule that no test calls the model is about the Anthropic chat path, which is
 * still mocked everywhere. It writes nothing, so it has nothing to clean up.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_USER", matches = ".+")
class PgVectorKnowledgeAdapterTest {

    @Autowired
    private KnowledgeSearchPort knowledge;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("the corpus is present and every chunk carries a source")
    void corpusIsIngested() {
        Integer chunks = jdbc.queryForObject(
                "SELECT count(*) FROM vector_store WHERE metadata->>'source' IS NOT NULL", Integer.class);
        assertThat(chunks).isNotNull().isPositive();
    }

    @Test
    @DisplayName("a question about damaged flowers retrieves the refund policy")
    void damagedOrderRetrievesRefundPolicy() {
        List<KnowledgePassage> passages = knowledge.search(
                KnowledgeQuery.of("me llegaron las flores dañadas, que hago?", Scope.COMMERCIAL));

        assertThat(passages).isNotEmpty();
        assertThat(passages).anySatisfy(passage ->
                assertThat(passage.content().toLowerCase())
                        .containsAnyOf("reembolso", "24 horas", "dañad"));
    }

    @Test
    @DisplayName("a question about quotation validity retrieves the five-day rule")
    void quotationValidityRetrievesFiveDays() {
        List<KnowledgePassage> passages = knowledge.search(
                KnowledgeQuery.of("por cuantos dias vale una cotizacion?", Scope.COMMERCIAL));

        assertThat(passages).anySatisfy(passage ->
                assertThat(passage.content()).contains("5 días"));
    }

    @Test
    @DisplayName("a commercial search never returns the botany book")
    void commercialScopeExcludesTheBook() {
        List<KnowledgePassage> passages = knowledge.search(
                KnowledgeQuery.of("como cuido mis flores para que duren mas?", Scope.COMMERCIAL));

        assertThat(passages).isNotEmpty();
        assertThat(passages).noneSatisfy(passage ->
                assertThat(passage.documentTitle()).containsIgnoringCase("Botanica"));
    }

    @Test
    @DisplayName("a botanical search never returns shop policy")
    void botanicalScopeExcludesThePolicy() {
        List<KnowledgePassage> passages = knowledge.search(
                KnowledgeQuery.of("que es el xilema y para que sirve?", Scope.BOTANICAL));

        assertThat(passages).isNotEmpty();
        assertThat(passages).noneSatisfy(passage ->
                assertThat(passage.documentTitle()).containsIgnoringCase("Spring-Bloom"));
    }

    @Test
    @DisplayName("passages come back ranked, best first")
    void passagesAreRanked() {
        List<KnowledgePassage> passages = knowledge.search(
                KnowledgeQuery.of("politica de importacion de flores", Scope.COMMERCIAL));

        assertThat(passages).isNotEmpty();
        assertThat(passages).isSortedAccordingTo(
                (a, b) -> Double.compare(b.score(), a.score()));
    }

    @Test
    @DisplayName("a question the corpus cannot answer returns nothing, not the least-bad chunk")
    void unrelatedQuestionReturnsNothing() {
        List<KnowledgePassage> passages = knowledge.search(
                KnowledgeQuery.of("como configuro el router wifi de mi casa?", Scope.COMMERCIAL));

        assertThat(passages).isEmpty();
    }

    @Test
    @DisplayName("a blank question is rejected before it is embedded")
    void blankQuestionRejected() {
        assertThatThrownBy(() -> KnowledgeQuery.of("  ", Scope.COMMERCIAL))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
