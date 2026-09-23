package com.springbloom.domain.model;

/**
 * What the trained vision model reported for a photo, carried back to the
 * caller alongside the agent's words.
 *
 * It exists so a UI can show that the species came from my  own
 * YOLOv11s-seg model and with what confidence, rather than leaving that claim
 * to be read out of the agent's prose - where it would be indistinguishable
 * from something the language model made up.
 *
 * @param decidedBy which of the two decided the species: the trained model, or
 *  the agent, once the model's confidence fell below the trust
 *  threshold
 */
public record VisionEvidence(
        String speciesKey,
        String commonName,
        String scientificName,
        int confidencePercent,
        boolean trusted,
        String modelName,
        Decider decidedBy) {

    /** The exported network behind every detection. Shown to the customer as provenance. */
    public static final String MODEL_NAME = "YOLOv11s-seg";
            // I do not  use Segementation, I do not know how to do it in Spring.
    public enum Decider {

        /** The trained model was confident enough that its answer stands. */
        TRAINED_MODEL,

        /** The model was below the trust threshold, so the agent judged the photo. */
        AGENT
    }

    public static VisionEvidence of(
            String speciesKey,
            String commonName,
            String scientificName,
            int confidencePercent,
            boolean trusted) {

        return new VisionEvidence(
                speciesKey, 
                commonName, 
                scientificName, 
                confidencePercent, 
                trusted,
                MODEL_NAME, trusted ? Decider.TRAINED_MODEL : Decider.AGENT
            );
    }

    /** Nothing scored above the detection threshold at all. */
    public static VisionEvidence nothingDetected() {
        return new VisionEvidence(null, 
            null, null, 
            0, 
            false, 
            MODEL_NAME, 
            Decider.AGENT);
    }
}
