package com.springbloom.adapter.out.vision;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.springbloom.adapter.out.catalog.FlowerCatalog;
import com.springbloom.domain.model.FlowerDetection;

import org.springframework.core.io.FileSystemResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Runs the real model against real photos. The weights are gitignored, so the
 * whole class is skipped rather than failed when they are absent.
 *
 * The model was trained on single-flower images: only the top detection is
 * treated as the answer, and no test asserts a count.
 */
class OnnxVisionAdapterTest {

    private static final Path MODEL = Path.of("vision_model.onnx");
    private static final Path IMAGES = Path.of("flowersImages");
    private static final int EXPECTED_CLASSES = 90;

    private static FlowerCatalog catalog;
    private static OnnxVisionAdapter adapter;

    @BeforeAll
    static void loadModel() {
        assumeTrue(Files.isRegularFile(MODEL), "vision_model.onnx not present, skipping vision tests");

        catalog = new FlowerCatalog(new ObjectMapper());
        adapter = new OnnxVisionAdapter(catalog, new FileSystemResource(MODEL), 0.25f, 0.45f);
    }

    @AfterAll
    static void closeModel() throws Exception {
        if (adapter != null) {
            adapter.close();
        }
    }

    @Test
    @DisplayName("model metadata and flowers.json name exactly the same species")
    void metadataMatchesCatalog() {
        String[] modelNames = adapter.classNames();
        assertThat(modelNames).hasSize(EXPECTED_CLASSES);

        Set<String> catalogKeys = catalog.all().stream()
                .map(species -> species.getSpeciesKey())
                .collect(Collectors.toSet());

        assertThat(catalogKeys)
                .as("flowers.json must match the model byte for byte")
                .containsExactlyInAnyOrder(modelNames);
    }

    /**
     * astromelia is alstroemeria, catalogued as peruvian_lily; bishop_of_llandaff
     * is the dahlia cultivar, the closest class to a plain dahlia.
     */
    @ParameterizedTest
    @CsvSource({
            "passion_flower.jpg,    passion_flower",
            "astromelia.jpg,        peruvian_lily",
            "yellow_water_lily.jpg, water_lily",
            "dahlia.jpg,            bishop_of_llandaff"
    })
    @DisplayName("each labelled photo resolves to its species as the top detection")
    void producesUsableDetections(String fileName, String expectedSpeciesKey) throws IOException {
        byte[] bytes = read(fileName);
        List<FlowerDetection> detections = adapter.classify(bytes);
        assertThat(detections).isNotEmpty();

        var image = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes));
        FlowerDetection top = detections.get(0);

        assertThat(top.speciesKey()).isEqualTo(expectedSpeciesKey);
        assertThat(catalog.find(top.speciesKey())).isPresent();
        assertThat(top.confidence()).isBetween(0.25, 1.0);
        assertThat(top.box().x()).isBetween(0.0, (double) image.getWidth());
        assertThat(top.box().y()).isBetween(0.0, (double) image.getHeight());
        assertThat(top.box().x() + top.box().width()).isLessThanOrEqualTo(image.getWidth() + 1.0);
        assertThat(top.box().y() + top.box().height()).isLessThanOrEqualTo(image.getHeight() + 1.0);
        assertThat(top.box().area()).isGreaterThan(0.0);

        System.out.printf("%s -> %s (%.3f)%n", fileName, top.speciesKey(), top.confidence());
    }

    @Test
    @DisplayName("detections come back ordered by confidence")
    void detectionsAreOrdered() {
        List<FlowerDetection> detections = adapter.classify(read("astromelia.jpg"));

        assertThat(detections).isSortedAccordingTo(
                Comparator.comparingDouble(FlowerDetection::confidence).reversed());
    }

    @Test
    @DisplayName("unreadable input is rejected, not passed to the model")
    void rejectsBadInput() {
        assertThatThrownBy(() -> adapter.classify(new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> adapter.classify("not an image".getBytes()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static byte[] read(String fileName) {
        Path path = IMAGES.resolve(fileName);
        assumeTrue(Files.isRegularFile(path), path + " not present, skipping");
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + path, e);
        }
    }
}
