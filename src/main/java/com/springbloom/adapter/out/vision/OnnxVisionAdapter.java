package com.springbloom.adapter.out.vision;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import com.springbloom.domain.model.FlowerDetection;
import com.springbloom.domain.port.out.FlowerCatalogPort;
import com.springbloom.domain.port.out.VisionClassifierPort;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

/**
 * Runs the YOLOv11s-seg export through ONNX Runtime. Boxes and class scores
 * only: the mask prototypes in output1 and the 32 mask coefficients in output0
 * are read past and discarded.
 */
@Component
public class OnnxVisionAdapter implements VisionClassifierPort, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(OnnxVisionAdapter.class);

    private static final int INPUT_SIZE = 640;
    private static final int BOX_CHANNELS = 4;
    private static final int MASK_COEFFICIENTS = 32;
    private static final int LETTERBOX_GREY = 114;

    /** Matches one "0: 'Artichoke'" pair inside the Python-dict names metadata. */
    private static final Pattern NAMES_ENTRY = Pattern.compile("(\\d+)\\s*:\\s*['\"]([^'\"]*)['\"]");

    private final FlowerCatalogPort catalog;
    private final float confidenceThreshold;
    private final float iouThreshold;

    private final OrtEnvironment environment;
    private final OrtSession session;
    private final String inputName;
    private final String[] classNames;

    public OnnxVisionAdapter(
            FlowerCatalogPort catalog,
            @Value("${florabelle.vision.model-path}") Resource modelPath,
            @Value("${florabelle.vision.confidence-threshold:0.25}") float confidenceThreshold,
            @Value("${florabelle.vision.iou-threshold:0.45}") float iouThreshold) {

        this.catalog = catalog;
        this.confidenceThreshold = confidenceThreshold;
        this.iouThreshold = iouThreshold;

        try {
            byte[] model = readModel(modelPath);
            this.environment = OrtEnvironment.getEnvironment();
            this.session = environment.createSession(model, new OrtSession.SessionOptions());
            this.inputName = session.getInputNames().iterator().next();
            this.classNames = readClassNames(session);

        } catch (OrtException e) {
            throw new IllegalStateException("Could not initialize ONNX session from " + modelPath, e);
        }

        log.info("Vision model loaded from {} with {} classes", modelPath, classNames.length);
    }

    private byte[] readModel(Resource modelPath) {

        try (InputStream in = modelPath.getInputStream()) {

            return in.readAllBytes();

        } catch (IOException e) {

            throw new IllegalStateException(
                    "Vision model not found at " + modelPath + ". The weights are gitignored "
                            + "and must be placed there manually, or VISION_MODEL_PATH set.", e);
        }
    }

    /** Class names come from the model's own metadata so they cannot drift from the export. */
    private String[] readClassNames(OrtSession session) throws OrtException {

        Map<String, String> metadata = session.getMetadata().getCustomMetadata();
        String raw = metadata.get("names");

        if (raw == null) {
            throw new IllegalStateException("Model metadata has no 'names' entry");
        }

        TreeMap<Integer, String> byIndex = new TreeMap<>();
        Matcher matcher = NAMES_ENTRY.matcher(raw);

        while (matcher.find()) {

            byIndex.put(Integer.parseInt(matcher.group(1)), matcher.group(2));
        }
        if (byIndex.isEmpty()) {

            throw new IllegalStateException("Could not parse 'names' metadata: " + raw);
        }

        int expected = byIndex.lastKey() + 1;

        if (byIndex.size() != expected) {

            throw new IllegalStateException("Model 'names' metadata has gaps in its indices");
        }
        return byIndex.values().toArray(new String[0]);
    }

    @Override
    public List<FlowerDetection> classify(byte[] imageBytes) {

        BufferedImage image = decode(imageBytes);
        Letterbox letterbox = Letterbox.forImage(image.getWidth(), image.getHeight());
        float[] input = toInputTensor(letterbox.apply(image));

        long[] shape = {1, 3, INPUT_SIZE, INPUT_SIZE};

        try (OnnxTensor tensor = OnnxTensor.createTensor(environment, FloatBuffer.wrap(input), shape);
             OrtSession.Result result = session.run(Map.of(inputName, tensor))) {

            OnnxTensor output = (OnnxTensor) result.get(0);
            long[] outputShape = output.getInfo().getShape();
            int channels = (int) outputShape[1];
            int anchors = (int) outputShape[2];

            float[] data = new float[(int) (outputShape[0] * channels * anchors)];
            output.getFloatBuffer().get(data);

            List<Candidate> candidates = decodeCandidates(data, channels, anchors, letterbox);
            return suppress(candidates);
            
        } catch (OrtException e) {
            throw new IllegalStateException("Vision inference failed", e);
        }
    }

    private BufferedImage decode(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            throw new IllegalArgumentException("Empty image");
        }
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (image == null) {
                throw new IllegalArgumentException("Unsupported image format");
            }
            return image;
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read image", e);
        }
    }

    /** NCHW, RGB, normalized to 0..1. */
    private float[] toInputTensor(BufferedImage letterboxed) {

        int pixels = INPUT_SIZE * INPUT_SIZE;
        float[] input = new float[3 * pixels];

        for (int y = 0; y < INPUT_SIZE; y++) {

            for (int x = 0; x < INPUT_SIZE; x++) {

                int rgb = letterboxed.getRGB(x, y);
                int offset = y * INPUT_SIZE + x;
                input[offset] = ((rgb >> 16) & 0xFF) / 255f;
                input[pixels + offset] = ((rgb >> 8) & 0xFF) / 255f;
                input[2 * pixels + offset] = (rgb & 0xFF) / 255f;
            }
        }
        return input;
    }

    /**
     * output0 is [channel][anchor], so element (c, a) sits at c * anchors + a.
     * There is no objectness channel in the v8/v11 head: confidence is the max
     * class score, already sigmoid.
     */
    private List<Candidate> decodeCandidates(float[] data, int channels, int anchors, Letterbox letterbox) {

        int numClasses = channels - BOX_CHANNELS - MASK_COEFFICIENTS;

        if (numClasses != classNames.length) {

            throw new IllegalStateException(
                    "Model outputs " + numClasses + " classes but metadata names " + classNames.length);
        }

        List<Candidate> candidates = new ArrayList<>();
        for (int anchor = 0; anchor < anchors; anchor++) {
            int bestClass = -1;
            float bestScore = confidenceThreshold;
            for (int c = 0; c < numClasses; c++) {
                float score = data[(BOX_CHANNELS + c) * anchors + anchor];
                if (score > bestScore) {
                    bestScore = score;
                    bestClass = c;
                }
            }
            if (bestClass < 0) {
                continue;
            }

            Optional<String> speciesKey = catalog.resolveByIndex(bestClass, classNames)
                    .map(species -> species.getSpeciesKey());
            if (speciesKey.isEmpty()) {
                log.warn("Model class {} ({}) is not in the catalog", bestClass, classNames[bestClass]);
                continue;
            }

            float cx = data[anchor];
            float cy = data[anchors + anchor];
            float w = data[2 * anchors + anchor];
            float h = data[3 * anchors + anchor];
            candidates.add(new Candidate(
                    bestClass, speciesKey.get(), bestScore, letterbox.toOriginal(cx, cy, w, h)));
        }
        return candidates;
    }

    /** Class-aware NMS, done here because the model was exported with nms=False. */
    private List<FlowerDetection> suppress(List<Candidate> candidates) {
        candidates.sort(Comparator.comparingDouble(Candidate::score).reversed());

        List<Candidate> kept = new ArrayList<>();
        for (Candidate candidate : candidates) {
            boolean overlaps = kept.stream()
                    .anyMatch(k -> k.classIndex() == candidate.classIndex()
                            && iou(k.box(), candidate.box()) > iouThreshold);
            if (!overlaps) {
                kept.add(candidate);
            }
        }

        return kept.stream()
                .map(c -> new FlowerDetection(c.speciesKey(), c.score(), c.box()))
                .toList();
    }

    private double iou(FlowerDetection.BoundingBox a, FlowerDetection.BoundingBox b) {
        
        double left = Math.max(a.x(), b.x());
        double top = Math.max(a.y(), b.y());
        double right = Math.min(a.x() + a.width(), b.x() + b.width());
        double bottom = Math.min(a.y() + a.height(), b.y() + b.height());

        double intersection = Math.max(0, right - left) * Math.max(0, bottom - top);
        double union = a.area() + b.area() - intersection;

        return union <= 0 ? 0 : intersection / union;
    }

    @Override
    public void close() throws OrtException {
        session.close();
    }

    /** The labels read from the model, in model index order. Visible for testing. */
    String[] classNames() {
        return classNames.clone();
    }

    /**
     * Aspect-preserving resize into a 640x640 grey canvas, plus the inverse
     * transform that puts boxes back into original image coordinates.
     */
    private record Letterbox(int originalWidth, int originalHeight, double scale, double padX, double padY) {

        static Letterbox forImage(int width, int height) {
            double scale = Math.min((double) INPUT_SIZE / width, (double) INPUT_SIZE / height);
            double padX = (INPUT_SIZE - width * scale) / 2;
            double padY = (INPUT_SIZE - height * scale) / 2;
            return new Letterbox(width, height, scale, padX, padY);
        }

        BufferedImage apply(BufferedImage source) {
            BufferedImage canvas = new BufferedImage(INPUT_SIZE, INPUT_SIZE, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = canvas.createGraphics();
            try {
                g.setColor(new Color(LETTERBOX_GREY, LETTERBOX_GREY, LETTERBOX_GREY));
                g.fillRect(0, 0, INPUT_SIZE, INPUT_SIZE);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(source, (int) Math.round(padX), (int) Math.round(padY),
                        (int) Math.round(originalWidth * scale),
                        (int) Math.round(originalHeight * scale), null);
            } finally {
                g.dispose();
            }
            return canvas;
        }

        /** Model space is 640-pixel centre/size, not normalized. */
        FlowerDetection.BoundingBox toOriginal(float cx, float cy, float w, float h) {
            double width = w / scale;
            double height = h / scale;
            double x = (cx - padX) / scale - width / 2;
            double y = (cy - padY) / scale - height / 2;

            double clampedX = Math.max(0, Math.min(x, originalWidth));
            double clampedY = Math.max(0, Math.min(y, originalHeight));
            return new FlowerDetection.BoundingBox(
                    clampedX,
                    clampedY,
                    Math.min(width, originalWidth - clampedX),
                    Math.min(height, originalHeight - clampedY));
        }
    }

    private record Candidate(
            int classIndex,
            String speciesKey,
            double score,
            FlowerDetection.BoundingBox box) {
    }
}
