package com.springbloom.adapter.out.persistence.repository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import com.springbloom.domain.model.FlowerSpecies;
import com.springbloom.domain.model.FlowerStock;
import com.springbloom.domain.model.ProductType;
import com.springbloom.domain.model.Quotation;
import com.springbloom.domain.model.QuotationItem;
import com.springbloom.domain.model.QuotationItemSpecies;
import com.springbloom.domain.model.QuotationStatus;
import com.springbloom.domain.port.out.FlowerSpeciesRepository;
import com.springbloom.domain.port.out.FlowerStockRepository;
import com.springbloom.domain.port.out.QuotationRepository;
import com.springbloom.domain.service.DocumentNumberGenerator;
import com.springbloom.domain.service.QuotationComposer;
import com.springbloom.domain.service.QuotationComposer.Selection;
import com.springbloom.domain.service.pricing.PricingStrategyFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The first real write on this path. Reads and ddl-auto: validate both pass on a
 * broken enum binding, so only an actual insert proves the mapping.
 *
 *   set -a; . ./.env; set +a; ./mvnw test
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_USER", matches = ".+")
class QuotationRepositoryAdapterTest {

    @Autowired
    private QuotationRepository quotations;

    @Autowired
    private QuotationJpaRepository jpaRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private FlowerSpeciesRepository species;

    @Autowired
    private FlowerStockRepository stock;

    @Autowired
    private PricingStrategyFactory strategies;

    @Autowired
    private JdbcTemplate jdbc;

    private final List<Long> written = new ArrayList<>();

    @AfterEach
    void removeWhatThisTestWrote() {
        written.forEach(id -> jdbc.update("DELETE FROM quotation WHERE id = ?", id));
        written.clear();
    }

    private Quotation record(Quotation saved) {
        written.add(saved.id());
        return saved;
    }

    private Selection selection(String speciesKey, int quantity) {
        FlowerSpecies found = species.findBySpeciesKey(speciesKey).orElseThrow();
        FlowerStock live = stock.findBySpeciesId(found.getId()).orElseThrow();
        return new Selection(found, live, quantity);
    }

    /** A two-line quotation: one INDIVIDUAL, one discounted BOUQUET of two species. */
    private Quotation composeSample() {
        QuotationComposer composer = new QuotationComposer(strategies);

        QuotationItem individual = composer.composeItem(
                ProductType.INDIVIDUAL, null, List.of(selection("rose", 3)));

        QuotationItem bouquet = composer.composeItem(
                ProductType.BOUQUET, new BigDecimal("10.00"),
                List.of(selection("Carnation", 6), selection("peruvian_lily", 4)));

        return composer.compose(List.of(individual, bouquet));
    }

    @Test
    @DisplayName("a real save inserts all three tables and reads back identical")
    void savesAndReadsBackTheWholeAggregate() {
        Quotation composed = composeSample();
        assertThat(composed.persisted()).isFalse();
        assertThat(composed.quotationNumber()).isNull();

        Quotation saved = record(quotations.save(composed));

        assertThat(saved.persisted()).isTrue();
        assertThat(saved.quotationNumber()).matches("COT-\\d{8}-\\d{5}");
        assertThat(saved.status()).isEqualTo(QuotationStatus.DRAFT);

        Quotation read = quotations.findById(saved.id()).orElseThrow();

        assertThat(read.quotationNumber()).isEqualTo(saved.quotationNumber());
        assertThat(read.status()).isEqualTo(QuotationStatus.DRAFT);
        assertThat(read.subtotal()).isEqualTo(composed.subtotal());
        assertThat(read.discountAmount()).isEqualTo(composed.discountAmount());
        assertThat(read.totalAmount()).isEqualTo(composed.totalAmount());
        assertThat(read.totalStems()).isEqualTo(13);

        assertThat(read.items()).hasSize(2);
        assertThat(read.items())
                .extracting(QuotationItem::productType)
                .containsExactlyInAnyOrder(ProductType.INDIVIDUAL, ProductType.BOUQUET);

        QuotationItem readBouquet = read.items().stream()
                .filter(item -> item.productType() == ProductType.BOUQUET)
                .findFirst().orElseThrow();

        assertThat(readBouquet.discountPercentage()).isEqualByComparingTo("10.00");
        assertThat(readBouquet.species()).hasSize(2);
        assertThat(readBouquet.composedSubtotal())
                .as("recomputed from the line_total snapshots, not stored")
                .isEqualTo(readBouquet.subtotal().plus(readBouquet.discountAmount()));

        assertThat(readBouquet.species())
                .extracting(QuotationItemSpecies::commonNameSnapshot)
                .doesNotContainNull();

        assertThat(rowCount("quotation_item", "quotation_id", saved.id())).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM quotation_item_species s"
                        + " JOIN quotation_item i ON i.id = s.quotation_item_id"
                        + " WHERE i.quotation_id = ?", Integer.class, saved.id()))
                .isEqualTo(3);
    }

    @Test
    @DisplayName("both Postgres enums bind on the write, not just on the read")
    void writesTheEnumColumnsAsNamedEnums() {
        Quotation saved = record(quotations.save(composeSample()));

        assertThat(jdbc.queryForObject(
                "SELECT status::text FROM quotation WHERE id = ?", String.class, saved.id()))
                .isEqualTo("DRAFT");

        assertThat(jdbc.queryForList(
                "SELECT product_type::text FROM quotation_item WHERE quotation_id = ?",
                String.class, saved.id()))
                .containsExactlyInAnyOrder("INDIVIDUAL", "BOUQUET");
    }

    @Test
    @DisplayName("the optional columns survive a round trip")
    void keepsConversationAndValidUntil() {
        UUID conversationId = jdbc.queryForObject(
                "SELECT id FROM conversation LIMIT 1", UUID.class);
        // TIMESTAMPTZ keeps microseconds, so a nanosecond instant never reads back equal.
        Instant until = Instant.now().plusSeconds(7 * 24 * 3600).truncatedTo(ChronoUnit.MICROS);

        Quotation saved = record(quotations.save(
                composeSample().withConversation(conversationId).withValidUntil(until)));

        Quotation read = quotations.findByNumber(saved.quotationNumber()).orElseThrow();

        assertThat(read.conversationId()).isEqualTo(conversationId);
        assertThat(read.validUntil()).isEqualTo(until);
        assertThat(quotations.findByConversationId(conversationId))
                .extracting(Quotation::id)
                .contains(saved.id());
    }

    @Test
    @DisplayName("a taken quotation number is retried, not surfaced as a failure")
    void retriesWhenTheNumberIsAlreadyTaken() {
        Quotation first = record(quotations.save(composeSample()));
        String taken = first.quotationNumber();

        QuotationRepositoryAdapter colliding = new QuotationRepositoryAdapter(
                jpaRepository, scriptedGenerator(taken, taken), transactionManager);

        Quotation second = record(colliding.save(composeSample()));

        assertThat(second.persisted()).isTrue();
        assertThat(second.quotationNumber()).isNotEqualTo(taken);
        assertThat(quotations.findByNumber(second.quotationNumber())).isPresent();
    }

    @Test
    @DisplayName("a number that never comes free fails loudly rather than looping")
    void givesUpAfterThreeCollisions() {
        Quotation first = record(quotations.save(composeSample()));
        String taken = first.quotationNumber();

        QuotationRepositoryAdapter alwaysColliding = new QuotationRepositoryAdapter(
                jpaRepository, scriptedGenerator(taken, taken, taken), transactionManager);

        Quotation doomed = composeSample();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> alwaysColliding.save(doomed))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("quotation number");
    }

    /** Hands out the given numbers in order, then falls back to genuinely random ones. */
    private DocumentNumberGenerator scriptedGenerator(String... scripted) {
        Deque<String> queue = new ArrayDeque<>(List.of(scripted));
        DocumentNumberGenerator real = new DocumentNumberGenerator(
                "COT", Clock.system(ZoneId.of("America/Bogota")), new java.security.SecureRandom());

        return new DocumentNumberGenerator("COT", Clock.systemUTC(), new java.security.SecureRandom()) {
            @Override
            public String next() {
                return Optional.ofNullable(queue.poll()).orElseGet(real::next);
            }
        };
    }

    private Integer rowCount(String table, String column, Object value) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?",
                Integer.class, value);
    }
}
