package com.springbloom.adapter.out.agent;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.springbloom.domain.service.guardrail.TurnFacts;

/**
 * Per-turn state shared by the guarded tool callbacks: how many tools ran,
 * which writes already ran, and which numbers the tools reported.
 */
final class TurnToolGuard {

    static final Set<String> WRITE_TOOLS = Set.of(
            "quoteSingleFlower", "quoteBouquet", "submitComplaint", "fileComplaint");

    private static final Pattern COP_FIELD = Pattern.compile("\"\\w*Cop\"\\s*:\\s*\"?(\\d+(?:\\.\\d+)?)\"?");

    private final int maxCalls;
    private final Set<String> writes = new HashSet<>();
    private int calls;
    private int refusals;
    private TurnFacts facts = TurnFacts.none();

    TurnToolGuard(int maxCalls) {
        this.maxCalls = maxCalls;
    }

    /** Null when the call may proceed, otherwise the JSON the model gets instead. */
    synchronized String admit(String tool, String input) {

        calls++;
        if (calls > maxCalls) {
            refusals++;
            return refusal("Se alcanzó el límite de consultas de este turno. "
                    + "Responda al cliente con lo que ya sabe.");
        }
        if (WRITE_TOOLS.contains(tool) && !writes.add(tool + ":" + input)) {
            refusals++;
            return refusal("Esa acción ya se realizó en este turno. No la repita.");
        }
        return null;
    }

    synchronized void observe(String output) {

        if (output == null) {
            return;
        }
        TurnFacts seen = TurnFacts.harvest(List.of(output));
        Matcher matcher = COP_FIELD.matcher(output);
        while (matcher.find()) {
            seen = seen.withAmount(new BigDecimal(matcher.group(1)));
        }
        facts = facts.with(seen);
    }

    synchronized TurnFacts facts() {
        return facts;
    }

    synchronized int refusals() {
        return refusals;
    }

    private static String refusal(String reason) {
        return "{\"refused\":true,\"reason\":\"" + reason + "\"}";
    }
}
