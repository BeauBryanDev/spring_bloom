package com.springbloom.application.usecase;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.springbloom.domain.model.FlowerDetection;
import com.springbloom.domain.model.FlowerSpecies;
import com.springbloom.domain.model.FlowerStock;
import com.springbloom.domain.port.in.ClassifyFlowerUseCase;
import com.springbloom.domain.port.out.FlowerSpeciesRepository;
import com.springbloom.domain.port.out.FlowerStockRepository;
import com.springbloom.domain.port.out.VisionClassifierPort;

/**
 * Photo to persisted species. The model is trained on single-flower images, so
 * only the top detection is considered.
 *
 */
@Service
public class ClassifyFlowerService implements ClassifyFlowerUseCase {

    private static final Logger log = LoggerFactory.getLogger(ClassifyFlowerService.class);

    private final VisionClassifierPort visionClassifier;
    private final FlowerSpeciesRepository speciesRepository;
    private final FlowerStockRepository stockRepository;

    /**
     * At or above this, the trained model's detection is the answer. Below it,
     * the detection is a suggestion the agent may overrule with its own reading
     * of the photo. Configurable so it can be retuned when the model is
     * retrained, without a rebuild.
     */
    private final double trustThreshold;

    public ClassifyFlowerService(
            VisionClassifierPort visionClassifier,
            FlowerSpeciesRepository speciesRepository,
            FlowerStockRepository stockRepository,
            @Value("${florabelle.vision.trust-threshold:0.5}") double trustThreshold) {

        this.visionClassifier = visionClassifier;
        this.speciesRepository = speciesRepository;
        this.stockRepository = stockRepository;
        this.trustThreshold = trustThreshold;
    }

    @Override
    public Optional<IdentifiedFlower> identify(ClassifyFlowerCommand command) {
        List<FlowerDetection> detections = visionClassifier.classify(command.imageBytes());

        if (detections.isEmpty()) {
            log.debug("No flower recognized for session {}", command.sessionKey());
            return Optional.empty();
        }

        FlowerDetection top = detections.get(0);
        Optional<FlowerSpecies> species = speciesRepository.findBySpeciesKey(top.speciesKey());

        if (species.isEmpty()) {
            log.warn("Model recognized {} but no such species is persisted", top.speciesKey());
            return Optional.empty();
        }

        Optional<FlowerStock> stock = stockRepository.findBySpeciesKey(top.speciesKey());
        if (stock.isEmpty()) {
            log.warn("Species {} has no flower_stock row, answering without a price", top.speciesKey());
        }

        boolean trusted = top.confidence() >= trustThreshold;

        log.info("Session {} identified as {} ({}) - trained model {}",
                command.sessionKey(), top.speciesKey(), top.confidence(),
                trusted ? "decides" : "below threshold, agent may overrule");

        return species.map(found ->
                new IdentifiedFlower(found, top.confidence(), stock, trusted));
    }
}
