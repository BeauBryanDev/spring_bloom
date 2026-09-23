package com.springbloom.adapter.out.knowledge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Pure, no Spring context: the chunker is static and reads no beans. */
class KnowledgeChunkerTest {

    private static String read(KnowledgeSource source) throws IOException {
        return new ClassPathResource(source.resourcePath())
                .getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("the manual splits on its numbered headings, one policy per chunk")
    void policySplitsBySection() throws IOException {
        List<KnowledgeChunk> chunks = KnowledgeChunker.chunk(
                KnowledgeSource.SPRING_BLOOM_POLICY, read(KnowledgeSource.SPRING_BLOOM_POLICY));

        assertThat(chunks).isNotEmpty();
        assertThat(chunks).allSatisfy(chunk ->
                assertThat(chunk.source()).isEqualTo(KnowledgeSource.SPRING_BLOOM_POLICY));

        assertThat(chunks).anySatisfy(chunk -> {
            assertThat(chunk.section()).contains("Precios y Cotizaciones");
            assertThat(chunk.content()).contains("validez estricta de calendario de 5 días");
        });
    }

    @Test
    @DisplayName("the refund policy is retrievable whole, not split across chunks")
    void refundPolicyStaysWhole() throws IOException {
        List<KnowledgeChunk> chunks = KnowledgeChunker.chunk(
                KnowledgeSource.SPRING_BLOOM_POLICY, read(KnowledgeSource.SPRING_BLOOM_POLICY));

        assertThat(chunks)
                .filteredOn(chunk -> chunk.section().contains("Reembolso"))
                .hasSize(1);
    }

    @Test
    @DisplayName("each FAQ question becomes its own chunk")
    void faqSplitsPerQuestion() throws IOException {
        List<KnowledgeChunk> chunks = KnowledgeChunker.chunk(
                KnowledgeSource.SPRING_BLOOM_POLICY, read(KnowledgeSource.SPRING_BLOOM_POLICY));

        List<KnowledgeChunk> faq = chunks.stream()
                .filter(chunk -> chunk.kind() == ChunkKind.FAQ)
                .toList();

        assertThat(faq).hasSizeGreaterThan(10);
        assertThat(faq).anySatisfy(chunk ->
                assertThat(chunk.content()).contains("INCOMING_RESTOCK"));
    }

    @Test
    @DisplayName("windowed sources produce overlapping chunks under the size budget")
    void windowsAreBoundedAndOverlap() throws IOException {
        List<KnowledgeChunk> chunks = KnowledgeChunker.chunk(
                KnowledgeSource.BOTANY_BOOK, read(KnowledgeSource.BOTANY_BOOK));

        assertThat(chunks).hasSizeGreaterThan(50);
        assertThat(chunks).allSatisfy(chunk ->
                assertThat(KnowledgeChunker.wordCount(chunk.content())).isLessThan(1200));

        String first = chunks.get(0).content();
        String second = chunks.get(1).content();
        String tail = first.substring(Math.max(0, first.length() - 80));
        assertThat(second).contains(tail.trim());
    }

    @Test
    @DisplayName("figure legends are their own kind, never mixed into prose")
    void figuresAreTaggedSeparately() throws IOException {
        List<KnowledgeChunk> figures = KnowledgeChunker.chunk(
                KnowledgeSource.BOTANY_FIGURES, read(KnowledgeSource.BOTANY_FIGURES));

        assertThat(figures).isNotEmpty();
        assertThat(figures).allSatisfy(chunk ->
                assertThat(chunk.kind()).isEqualTo(ChunkKind.FIGURE));

        List<KnowledgeChunk> prose = KnowledgeChunker.chunk(
                KnowledgeSource.BOTANY_BOOK, read(KnowledgeSource.BOTANY_BOOK));
        assertThat(prose).noneSatisfy(chunk ->
                assertThat(chunk.kind()).isEqualTo(ChunkKind.FIGURE));
    }

    @Test
    @DisplayName("metadata carries the source key the retrieval filters use")
    void metadataCarriesSourceKey() throws IOException {
        KnowledgeChunk chunk = KnowledgeChunker.chunk(
                KnowledgeSource.COLOMBIA_MARKET, read(KnowledgeSource.COLOMBIA_MARKET)).get(0);

        Map<String, Object> metadata = chunk.metadata();
        assertThat(metadata).containsEntry("source", "colombia_market");
        assertThat(metadata).containsEntry("kind", "PROSE");
        assertThat(metadata.get("title")).asString().isNotBlank();
    }

    @Test
    @DisplayName("a blank chunk is rejected at construction")
    void blankContentRejected() {
        assertThat(java.util.stream.Stream.of("", "   ")).allSatisfy(blank ->
                org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                                KnowledgeChunk.prose(KnowledgeSource.WORLD_MARKET, "s", blank))
                        .isInstanceOf(IllegalArgumentException.class));
    }
}
