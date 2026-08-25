package com.springbloom.domain.model;

/**
 * One flower found in a customer image. speciesKey is a catalog key, never a
 * model index, so nothing downstream depends on how the model was exported.
 */
public record FlowerDetection(
        String speciesKey,
        double confidence,
        BoundingBox box) {

    public FlowerDetection {
        if (speciesKey == null || speciesKey.isBlank()) {
            throw new IllegalArgumentException("speciesKey is required");
        }
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence out of range: " + confidence);
        }
    }

    /** Pixel coordinates in the original image, top-left origin. */
    public record BoundingBox(double x, double y, double width, double height) {

        public BoundingBox {
            if (width < 0 || height < 0) {
                throw new IllegalArgumentException("Negative box size");
            }
        }

        public double area() {
            return width * height;
        }
    }
}
