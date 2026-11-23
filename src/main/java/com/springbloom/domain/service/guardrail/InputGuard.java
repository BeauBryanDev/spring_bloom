package com.springbloom.domain.service.guardrail;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Screens a customer's words before any model sees them. Pure and
 * deterministic. It matches phrases, never single words: a false block costs
 * a customer an answer.
 */
public final class InputGuard {

    private static final Pattern INVISIBLE = Pattern.compile("[\\u200B-\\u200F\\u2060\\uFEFF\\u202A-\\u202E\\p{Cc}&&[^\\n\\r\\t]]");
    private static final Pattern RUN = Pattern.compile("(.)\\1{59,}");
    private static final Pattern WORD_LOOP = Pattern.compile("\\b(\\w+)(?:\\s+\\1\\b){29,}");
    private static final Pattern LINK = Pattern.compile("https?://|www\\.");
    private static final int MAX_LINKS = 3;

    /** Patterns run against lowercased, accent-free, whitespace-collapsed text. */
    private static final Map<String, Pattern> RULES = new LinkedHashMap<>();

    static {
        RULES.put("override_instructions", Pattern.compile(
                "\\b(ignora|ignores?|olvida|omite|desestima|disregard|forget|override)\\b.{0,40}"
                + "\\b(instrucciones|indicaciones|reglas|prompt|anteriores|forget|previous|prior|instructions|rules|above)\\b"));
        RULES.put("extract_prompt", Pattern.compile(
                "system prompt|prompt del sistema|prompt de sistema|"
                + "\\b(muestra|muestrame|dime|revela|repite|imprime|escribe|show|reveal|print|repeat|tell)\\b.{0,40}"
                + "\\b(tu prompt|tus instrucciones|your prompt|your instructions|instrucciones (del sistema|internas|iniciales|originales))\\b"));
        RULES.put("role_hijack", Pattern.compile(
                "modo (desarrollador|dios)|developer mode|jailbreak|do anything now|"
                + "\\b(eres ahora|ahora eres|ahora seras|seras ahora)\\b|\\bfinge (ser|que eres)\\b|\\bpretend (to be|you are)\\b|\\bact as\\b.{0,20}\\bwithout\\b"));
        RULES.put("fake_markup", Pattern.compile(
                "</?(system|assistant)>|\\[(inst|system)\\]|<\\|im_start\\|>|<\\|im_end\\|>"));
    }

    private InputGuard() {
    }

    public static GuardVerdict check(String message) {

        if (message == null || message.isBlank()) {
            return GuardVerdict.pass();
        }
        if (INVISIBLE.matcher(message).find()) {
            return GuardVerdict.broke("invisible_characters");
        }

        String folded = fold(message);

        if (RUN.matcher(folded).find() || WORD_LOOP.matcher(folded).find()) {
            return GuardVerdict.broke("repetition");
        }
        if (LINK.matcher(folded).results().count() > MAX_LINKS) {
            return GuardVerdict.broke("link_flood");
        }
        for (Map.Entry<String, Pattern> rule : RULES.entrySet()) {
            if (rule.getValue().matcher(folded).find()) {
                return GuardVerdict.broke(rule.getKey());
            }
        }
        return GuardVerdict.pass();
    }

    static String fold(String text) {
        return Normalizer.normalize(text.toLowerCase(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
