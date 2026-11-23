package com.springbloom.domain.service.guardrail;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The document numbers and COP amounts a reply is allowed to mention: those
 * a tool returned this turn, plus those already present in the conversation.
 */
public record TurnFacts(Set<String> documentNumbers, Set<String> amounts) {

    static final Pattern DOCUMENT = Pattern.compile("\\b(?:COT|ORD|REC)-\\d{8}-\\d{5}\\b", Pattern.CASE_INSENSITIVE);

    private static final String NUMBER = "\\d{1,3}(?:[.,]\\d{3})+|\\d+";

    static final Pattern AMOUNT = Pattern.compile(
            "\\$\\s?(" + NUMBER + ")(?:[.,]\\d{1,2}(?!\\d))?"
            + "|\\b(" + NUMBER + ")(?:[.,]\\d{1,2}(?!\\d))?\\s*(?:COP|pesos)\\b",
            Pattern.CASE_INSENSITIVE);

    public TurnFacts {
        documentNumbers = Set.copyOf(documentNumbers);
        amounts = Set.copyOf(amounts);
    }

    public static TurnFacts none() {
        return new TurnFacts(Set.of(), Set.of());
    }

    /** Everything number-like in the given texts (history, tool passages). */
    public static TurnFacts harvest(Collection<String> texts) {

        Set<String> numbers = new HashSet<>();
        Set<String> amounts = new HashSet<>();

        for (String text : texts) {
            if (text != null) {
                numbers.addAll(documentNumbersIn(text));
                amounts.addAll(amountsIn(text));
            }
        }
        return new TurnFacts(numbers, amounts);
    }

    public TurnFacts with(TurnFacts other) {

        Set<String> numbers = new HashSet<>(documentNumbers);
        Set<String> money = new HashSet<>(amounts);
        numbers.addAll(other.documentNumbers);
        money.addAll(other.amounts);
        return new TurnFacts(numbers, money);
    }

    public TurnFacts withDocumentNumber(String number) {
        return number == null ? this : with(new TurnFacts(Set.of(number.toUpperCase(Locale.ROOT)), Set.of()));
    }

    public TurnFacts withAmount(BigDecimal amount) {
        return amount == null ? this : with(new TurnFacts(Set.of(), Set.of(normalise(amount))));
    }

    static Set<String> documentNumbersIn(String text) {

        Set<String> found = new HashSet<>();
        Matcher matcher = DOCUMENT.matcher(text);
        while (matcher.find()) {
            found.add(matcher.group().toUpperCase(Locale.ROOT));
        }
        return found;
    }

    static Set<String> amountsIn(String text) {

        Set<String> found = new HashSet<>();
        Matcher matcher = AMOUNT.matcher(text);
        while (matcher.find()) {
            String digits = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            found.add(digits.replaceAll("[.,]", ""));
        }
        return found;
    }

    /** Whole pesos, no separators: how an amount is compared. */
    static String normalise(BigDecimal amount) {
        return amount.setScale(0, RoundingMode.HALF_UP).toPlainString();
    }
}
