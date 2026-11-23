package com.springbloom.domain.service.guardrail;

/** The only customer-facing text the guardrails may produce. */
public final class GuardrailMessages {

    public static final String INPUT_REFUSAL =
            "Disculpe, solo puedo ayudarle con flores, cotizaciones y pedidos de Springbloom. "
            + "¿Qué flor o arreglo le gustaría conocer?";

    public static final String OUTPUT_FALLBACK =
            "Disculpe, no puedo confirmarle eso con seguridad. "
            + "Nuestro equipo comercial lo revisará y se lo confirmará. "
            + "¿Puedo ayudarle con otra consulta sobre nuestras productos";

    public static final String RATE_LIMITED =
            "Está escribiendo muy rápido para mí. Por favor espere unos segundos e intente de nuevo.";

    public static final String MODEL_UNAVAILABLE =
            "Disculpe, en este momento no puedo atenderle. Por favor intente de nuevo en unos minutos.";

    private GuardrailMessages() {
    }
}
