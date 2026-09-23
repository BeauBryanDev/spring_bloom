package com.springbloom.application.usecase;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.springbloom.domain.model.FlowerDetection;
import com.springbloom.domain.model.FlowerSpecies;
import com.springbloom.domain.model.FlowerStock;
import com.springbloom.domain.model.FlowerStockStatus;
import com.springbloom.domain.model.vo.Money;
import com.springbloom.domain.port.in.ClassifyFlowerUseCase.ClassifyFlowerCommand;
import com.springbloom.domain.port.in.ClassifyFlowerUseCase.IdentifiedFlower;
import com.springbloom.domain.port.out.FlowerSpeciesRepository;
import com.springbloom.domain.port.out.FlowerStockRepository;
import com.springbloom.domain.port.out.VisionClassifierPort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Orchestration only: the model and the database are both mocked. */
class ClassifyFlowerServiceTest {

    private static final byte[] IMAGE = {1, 2, 3};

    /** Mirrors florabelle.vision.trust-threshold; the bands are asserted against it. */
    private static final double TRUST_THRESHOLD = 0.5;
    private static final String SESSION = "session-abc";

    private static final FlowerDetection ROSE_DETECTION = new FlowerDetection(
            "rose", 0.93, new FlowerDetection.BoundingBox(10, 10, 100, 100));

    private static final FlowerSpecies PERSISTED_ROSE = new FlowerSpecies(
            7L, "rose", "Rosa", "Rosa gallica", "Ecuador", null, null);

    private VisionClassifierPort visionClassifier;
    private FlowerSpeciesRepository speciesRepository;
    private FlowerStockRepository stockRepository;
    private ClassifyFlowerService service;

    private static FlowerStock stock(FlowerStockStatus status, int quantity, String price, String multiplier) {
        return new FlowerStock(1L, 7L, status, quantity, null,
                Money.of(price), new BigDecimal(multiplier), Instant.now());
    }

    @BeforeEach
    void setUp() {
        visionClassifier = mock(VisionClassifierPort.class);
        speciesRepository = mock(FlowerSpeciesRepository.class);
        stockRepository = mock(FlowerStockRepository.class);
        service = new ClassifyFlowerService(
                visionClassifier, speciesRepository, stockRepository, TRUST_THRESHOLD);
    }

    @Test
    @DisplayName("a recognized flower resolves to the persisted species")
    void identifiesPersistedSpecies() {
        when(visionClassifier.classify(IMAGE)).thenReturn(List.of(ROSE_DETECTION));
        when(speciesRepository.findBySpeciesKey("rose")).thenReturn(Optional.of(PERSISTED_ROSE));

        Optional<IdentifiedFlower> result = service.identify(new ClassifyFlowerCommand(IMAGE, SESSION));

        assertThat(result).isPresent();
        assertThat(result.get().speciesKey()).isEqualTo("rose");
        assertThat(result.get().confidence()).isEqualTo(0.93);
        assertThat(result.get().species().getId())
                .as("the database id, not the catalog stub")
                .isEqualTo(7L);
    }

    @Test
    @DisplayName("only the top detection is looked up")
    void ignoresLowerConfidenceDetections() {
        FlowerDetection weaker = new FlowerDetection(
                "sunflower", 0.40, new FlowerDetection.BoundingBox(0, 0, 10, 10));
        when(visionClassifier.classify(IMAGE)).thenReturn(List.of(ROSE_DETECTION, weaker));
        when(speciesRepository.findBySpeciesKey("rose")).thenReturn(Optional.of(PERSISTED_ROSE));

        service.identify(new ClassifyFlowerCommand(IMAGE, SESSION));

        verify(speciesRepository).findBySpeciesKey("rose");
        verify(speciesRepository, never()).findBySpeciesKey("sunflower");
    }

    @Test
    @DisplayName("nothing recognized is an empty answer, and the database is never touched")
    void returnsEmptyWhenNothingDetected() {
        when(visionClassifier.classify(IMAGE)).thenReturn(List.of());

        assertThat(service.identify(new ClassifyFlowerCommand(IMAGE, SESSION))).isEmpty();
        verify(speciesRepository, never()).findBySpeciesKey(anyString());
    }

    @Test
    @DisplayName("a species the model knows but the database does not is an empty answer")
    void returnsEmptyWhenSpeciesNotPersisted() {
        when(visionClassifier.classify(IMAGE)).thenReturn(List.of(ROSE_DETECTION));
        when(speciesRepository.findBySpeciesKey("rose")).thenReturn(Optional.empty());

        assertThat(service.identify(new ClassifyFlowerCommand(IMAGE, SESSION))).isEmpty();
    }

    @Test
    @DisplayName("a sellable flower comes back with its effective price")
    void attachesPriceAndAvailability() {
        when(visionClassifier.classify(IMAGE)).thenReturn(List.of(ROSE_DETECTION));
        when(speciesRepository.findBySpeciesKey("rose")).thenReturn(Optional.of(PERSISTED_ROSE));
        when(stockRepository.findBySpeciesKey("rose"))
                .thenReturn(Optional.of(stock(FlowerStockStatus.IN_STOCK, 30, "4448.00", "1.000")));

        IdentifiedFlower flower = service.identify(new ClassifyFlowerCommand(IMAGE, SESSION)).orElseThrow();

        assertThat(flower.quotable()).isTrue();
        assertThat(flower.unitPrice()).contains(Money.of("4448.00"));
        assertThat(flower.stock().orElseThrow().availableImmediately()).isTrue();
    }

    @Test
    @DisplayName("an imported flower is quoted at base price times its multiplier")
    void appliesImportMultiplier() {
        when(visionClassifier.classify(IMAGE)).thenReturn(List.of(ROSE_DETECTION));
        when(speciesRepository.findBySpeciesKey("rose")).thenReturn(Optional.of(PERSISTED_ROSE));
        when(stockRepository.findBySpeciesKey("rose"))
                .thenReturn(Optional.of(stock(FlowerStockStatus.IMPORT_ON_REQUEST, 0, "12000.00", "1.400")));

        IdentifiedFlower flower = service.identify(new ClassifyFlowerCommand(IMAGE, SESSION)).orElseThrow();

        assertThat(flower.unitPrice()).contains(Money.of("16800.00"));
        assertThat(flower.quotable()).as("sellable with a lead time").isTrue();
        assertThat(flower.stock().orElseThrow().availableImmediately()).isFalse();
    }

    @Test
    @DisplayName("a NOT_FOR_SALE flower is identified but never priced")
    void neverQuotesUnsellableSpecies() {
        when(visionClassifier.classify(IMAGE)).thenReturn(List.of(ROSE_DETECTION));
        when(speciesRepository.findBySpeciesKey("rose")).thenReturn(Optional.of(PERSISTED_ROSE));
        when(stockRepository.findBySpeciesKey("rose"))
                .thenReturn(Optional.of(stock(FlowerStockStatus.NOT_FOR_SALE, 0, "9000.00", "1.000")));

        IdentifiedFlower flower = service.identify(new ClassifyFlowerCommand(IMAGE, SESSION)).orElseThrow();

        assertThat(flower.speciesKey()).as("still identified for the customer").isEqualTo("rose");
        assertThat(flower.quotable()).isFalse();
        assertThat(flower.unitPrice())
                .as("a price must never leak for a flower that may not be sold")
                .isEmpty();
    }

    @Test
    @DisplayName("a species with no stock row is still identified, without a price")
    void survivesMissingStockRow() {
        when(visionClassifier.classify(IMAGE)).thenReturn(List.of(ROSE_DETECTION));
        when(speciesRepository.findBySpeciesKey("rose")).thenReturn(Optional.of(PERSISTED_ROSE));
        when(stockRepository.findBySpeciesKey("rose")).thenReturn(Optional.empty());

        IdentifiedFlower flower = service.identify(new ClassifyFlowerCommand(IMAGE, SESSION)).orElseThrow();

        assertThat(flower.speciesKey()).isEqualTo("rose");
        assertThat(flower.stock()).isEmpty();
        assertThat(flower.quotable()).isFalse();
        assertThat(flower.unitPrice()).isEmpty();
    }

    @Test
    @DisplayName("an invalid command is rejected before the model runs")
    void rejectsInvalidCommands() {
        assertThatThrownBy(() -> new ClassifyFlowerCommand(new byte[0], SESSION))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new ClassifyFlowerCommand(IMAGE, " "))
                .isInstanceOf(IllegalArgumentException.class);

        verify(visionClassifier, never()).classify(any());
    }
    @Test
    @DisplayName("above the trust threshold the trained model's answer stands")
    void trustsAConfidentDetection() {
        when(visionClassifier.classify(IMAGE)).thenReturn(List.of(detection(0.93)));
        when(speciesRepository.findBySpeciesKey("rose")).thenReturn(Optional.of(PERSISTED_ROSE));

        IdentifiedFlower flower = service.identify(new ClassifyFlowerCommand(IMAGE, SESSION)).orElseThrow();

        assertThat(flower.trusted()).isTrue();
        assertThat(flower.confidencePercent()).isEqualTo(93);
    }

    @Test
    @DisplayName("below the trust threshold the detection is only a suggestion")
    void doesNotTrustAWeakDetection() {
        when(visionClassifier.classify(IMAGE)).thenReturn(List.of(detection(0.34)));
        when(speciesRepository.findBySpeciesKey("rose")).thenReturn(Optional.of(PERSISTED_ROSE));

        IdentifiedFlower flower = service.identify(new ClassifyFlowerCommand(IMAGE, SESSION)).orElseThrow();

        assertThat(flower.trusted()).isFalse();
        assertThat(flower.confidencePercent()).isEqualTo(34);
    }

    @Test
    @DisplayName("the threshold itself is trusted, so the band is closed at the bottom")
    void trustsExactlyAtTheThreshold() {
        when(visionClassifier.classify(IMAGE)).thenReturn(List.of(detection(TRUST_THRESHOLD)));
        when(speciesRepository.findBySpeciesKey("rose")).thenReturn(Optional.of(PERSISTED_ROSE));

        IdentifiedFlower flower = service.identify(new ClassifyFlowerCommand(IMAGE, SESSION)).orElseThrow();

        assertThat(flower.trusted()).isTrue();
        assertThat(flower.confidencePercent()).isEqualTo(50);
    }

    @Test
    @DisplayName("the percentage is rounded half-up, never truncated")
    void roundsTheConfidencePercentage() {
        when(visionClassifier.classify(IMAGE)).thenReturn(List.of(detection(0.9282598)));
        when(speciesRepository.findBySpeciesKey("rose")).thenReturn(Optional.of(PERSISTED_ROSE));

        IdentifiedFlower flower = service.identify(new ClassifyFlowerCommand(IMAGE, SESSION)).orElseThrow();

        assertThat(flower.confidencePercent()).isEqualTo(93);
    }

    private static FlowerDetection detection(double confidence) {
        return new FlowerDetection(
                "rose", confidence, new FlowerDetection.BoundingBox(10, 10, 100, 100));
    }
}
