package com.springbloom.domain.service.guardrail;

/** The outcome of a guard: it passed, or it broke the named rule. */
public record GuardVerdict(boolean passed, String rule) {

    private static final GuardVerdict PASS = new GuardVerdict(true, null);

    public static GuardVerdict pass() {
        return PASS;
    }

    public static GuardVerdict broke(String rule) {
        return new GuardVerdict(false, rule);
    }
}
