package com.springbloom.domain.service;

import java.util.Optional;

/** Local mobile numbers only: ten digits, starting with 3. No country code is ever added or removed. */
public final class ColombianPhone {

    private ColombianPhone() {
    }

    /** The ten digits, or empty when the text is not a Colombian mobile number. */
    public static Optional<String> normalize(String raw) {
        if (raw == null) {

            return Optional.empty();
        }
        String digits = raw.replaceAll("\\D", "");  
        // \D  means "not a digit" in regex, so this removes all non-digits.

        return digits.length() == 10 && digits.charAt(0) == '3'
                ? Optional.of(digits)
                : Optional.empty();
    }  // It accepts the result only if it has exactly ten digits and the first is 3

    /** Both sides are reduced the same way, so formatting never decides a match. */
    public static boolean sameNumber(String a, String b) {

        Optional<String> left = normalize(a);

        return left.isPresent() && left.equals(normalize(b));
    }
}
