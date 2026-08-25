package com.springbloom.domain.port.out;

import com.springbloom.domain.model.FlowerDetection;

import java.util.List;

/**
 * Recognizes flower species in a customer image. Takes raw bytes so the domain
 * stays free of any image or inference type.
 */
public interface VisionClassifierPort {

    /** Detections above the configured confidence, most confident first. Empty if none. */
    List<FlowerDetection> classify(byte[] imageBytes);
}
