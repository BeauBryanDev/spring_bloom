package com.springbloom.domain.model.vo;

import java.util.Arrays;
import java.util.Set;

/**
 * A photo a customer attached to a chat turn.
 *
 * The bytes are the truth. A browser's reported content type is a hint the
 * caller cannot trust, so the type is decided here by reading the file's magic
 * number - a .png renamed to .jpg would otherwise reach the model declared as
 * something it is not, and Anthropic rejects the turn rather than guessing.
 */
public record ImageAttachment(byte[] bytes, String mimeType) {

    /** What the vision path and the model both accept. */
    public static final Set<String> SUPPORTED = Set.of("image/jpeg", "image/png", "image/webp");

    /**
     * A chat attachment is a photograph, not an upload. Matches the 10MB
     * multipart limit in application.yml so the two edges agree.
     */
    public static final int MAX_BYTES = 10 * 1024 * 1024;

    public ImageAttachment {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("An image is required");
        }
        if (bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException(
                    "An image cannot exceed " + (MAX_BYTES / (1024 * 1024)) + "MB");
        }
        if (!SUPPORTED.contains(mimeType)) {
            throw new IllegalArgumentException("Unsupported image type: " + mimeType);
        }
        bytes = bytes.clone();
    }

    /**
     * Builds an attachment from bytes alone, ignoring whatever the client
     * claimed the type was.
     *
     * @throws IllegalArgumentException if the bytes are not a JPEG, PNG or WebP
     */
    public static ImageAttachment of(byte[] bytes) {
        return new ImageAttachment(bytes, sniff(bytes));
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }

    public int sizeInBytes() {
        return bytes.length;
    }

    /**
     * The file's own declaration of what it is. Deliberately not derived from a
     * filename extension: an extension is a customer's guess, a magic number is
     * the file.
     */
    private static String sniff(byte[] bytes) {

        if (bytes == null || bytes.length < 12) {
            throw new IllegalArgumentException("That file is not a readable image");
        }
        if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        if (Arrays.equals(Arrays.copyOfRange(bytes, 0, 8), png)) {
            return "image/png";
        }
        if (bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return "image/webp";
        }
        throw new IllegalArgumentException(
                "Only JPEG, PNG and WebP images are supported");
    }

    @Override
    public boolean equals(Object other) {
        
        return other instanceof ImageAttachment image
                && mimeType.equals(image.mimeType)
                && Arrays.equals(bytes, image.bytes);
    }

    @Override
    public int hashCode() {
        return 31 * mimeType.hashCode() + Arrays.hashCode(bytes);
    }

    @Override
    public String toString() {
        return "ImageAttachment[" + mimeType + ", " + bytes.length + " bytes]";
    }
}
