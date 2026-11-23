package com.springbloom.domain.service.guardrail;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Checks a reply against what the tools actually returned. Pure and
 * deterministic: it enforces the prompt's promises that nothing else enforces.
 */
public final class OutputGuard {

    /** Patterns run against lowercased, accent-free, whitespace-collapsed text. */
    private static final Map<String, Pattern> RULES = new LinkedHashMap<>();

    static {
        RULES.put("promised_refund", Pattern.compile(
                "\\b(le|les|lo|la)\\s+(reembols\\w*|devolv\\w+|reintegr\\w+)|"
                + "\\bgarantizo\\b.{0,20}\\b(reembolso|devolucion)\\b|"
                + "\\b(le|les)\\s+(enviaremos|enviamos|mandaremos|mandamos|haremos llegar)\\s+(otro|otra|otros|otras|un reemplazo)\\b"));
        RULES.put("offered_discount", Pattern.compile(
                "\\b(le|les)\\s+(ofrezco|ofrecemos|doy|damos|aplico|aplicamos|concedo|concedemos)\\b.{0,30}\\bdescuento\\b|"
                + "\\b(puedo|podemos)\\s+(darle|ofrecerle|aplicarle|concederle)\\b.{0,25}\\bdescuento\\b"));
        RULES.put("promised_response_time", Pattern.compile(
                "\\b(responderemos|contactaremos|escribiremos|resolveremos|llamaremos)\\b.{0,40}\\b(en|dentro de)\\s+\\d+\\s+(minutos|horas|dias)\\b"));
        RULES.put("asked_for_card_data", Pattern.compile(
                "\\b(envie|envieme|comparta|dicteme|indiqueme|digame|deme|ingrese|escriba|enviame|pasame)\\b.{0,40}"
                + "\\b(numero de (su )?tarjeta|cvv|cvc|contrasena|codigo de seguridad)\\b"));
        RULES.put("leaked_internals", Pattern.compile(
                "\\b(claude|anthropic|haiku|openai|gpt)\\b|"
                + "\\b(findflowers|quotesingleflower|quotebouquet|identifyflowerinphoto|lookuporder|submitcomplaint|"
                + "filecomplaint|placeorder|searchpolicy|explainbotany)\\b|"
                + "lo que no haces|system prompt|mis instrucciones (dicen|son)"));
    }

    private OutputGuard() {
    }

    public static GuardVerdict check(String reply, TurnFacts known) {

        if (reply == null || reply.isBlank()) {
            return GuardVerdict.pass();
        }
        for (String number : TurnFacts.documentNumbersIn(reply)) {
            if (!known.documentNumbers().contains(number)) {
                return GuardVerdict.broke("invented_document_number");
            }
        }
        for (String amount : TurnFacts.amountsIn(reply)) {
            if (!known.amounts().contains(amount)) {
                return GuardVerdict.broke("unbacked_amount");
            }
        }

        String folded = InputGuard.fold(reply);

        for (Map.Entry<String, Pattern> rule : RULES.entrySet()) {
            if (rule.getValue().matcher(folded).find()) {
                return GuardVerdict.broke(rule.getKey());
            }
        }
        return GuardVerdict.pass();
    }
}
