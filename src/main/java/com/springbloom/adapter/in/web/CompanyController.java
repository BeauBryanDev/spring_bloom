package com.springbloom.adapter.in.web;

import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.springbloom.domain.model.FlowerStockStatus;

/**
 * The three static company pages: "Sobre Nosotros", "Contacto" and "Como Funciona".
 *
 * Their content is the corporate manual - the same document the agent retrieves
 * from through searchPolicy - so the page a customer reads and the answer
 * Florabelle gives are the same policy. The prose lives in the templates; only
 * the repeated card and step lists are here, because a Thymeleaf loop over a
 * record reads better than twenty hand-copied blocks.
 */
@Controller
public class CompanyController {

    @GetMapping("/sobre-nosotros")
    public String aboutUs(Model model) {
        model.addAttribute("activePage", "sobre-nosotros");
        model.addAttribute("values", VALUES);
        model.addAttribute("objectives", OBJECTIVES);
        return "pages/aboutus";
    }

    @GetMapping("/contacto")
    public String contact(Model model) {
        model.addAttribute("activePage", "contacto");
        return "pages/contact";
    }

    @GetMapping("/como-funciona")
    public String howItWorks(Model model) {
        model.addAttribute("activePage", "como-funciona");
        model.addAttribute("steps", STEPS);
        model.addAttribute("availability", availability());
        return "pages/howitworks";
    }

    /**
     * The four stock states, straight off the enum. The label is never retyped
     * here: FlowerStockStatus owns the customer-facing Spanish, and a second
     * copy on this page would be a parallel vocabulary waiting to drift.
     */
    private static List<AvailabilityState> availability() {
        return Arrays.stream(FlowerStockStatus.values())
                .map(status -> new AvailabilityState(
                        status.name(), status.label(), DESCRIPTIONS.get(status)))
                .toList();
    }

    private static final java.util.Map<FlowerStockStatus, String> DESCRIPTIONS = java.util.Map.of(
            FlowerStockStatus.IN_STOCK,
            "Producto disponible en inventario fisico en bodega, listo para preparacion inmediata.",
            FlowerStockStatus.INCOMING_RESTOCK,
            "Producto agotado temporalmente, pero con lote confirmado de llegada desde finca en "
                    + "menos de 24 horas.",
            FlowerStockStatus.IMPORT_ON_REQUEST,
            "Producto disponible exclusivamente mediante importacion programada, con tiempo de "
                    + "entrega extendido de minimo 5 a 7 dias habiles.",
            FlowerStockStatus.NOT_FOR_SALE,
            "Producto fuera de temporada o retirado por control de calidad.");

    private static final List<CompanyValue> VALUES = List.of(
            new CompanyValue("Calidad incomprometida",
                    "Cada flor que sale de Spring-Bloom cumple con rigurosos estandares esteticos "
                            + "y biologicos.",
                    "Se rechazan lotes completos de proveedores si superan el umbral de "
                            + "imperfecciones permitidas, aun si eso genera escasez temporal."),
            new CompanyValue("Confiabilidad",
                    "Cumplimiento estricto de horarios de entrega, disponibilidad de stock y "
                            + "especificaciones de cotizacion.",
                    "Si un producto no esta garantizado en el inventario real, no se vende ni se "
                            + "promete."),
            new CompanyValue("Transparencia",
                    "Claridad absoluta en precios, costos de envio, politicas de reembolso y "
                            + "origen de las flores.",
                    "No existen costos ocultos en las cotizaciones generadas por Florabelle o por "
                            + "el equipo comercial."),
            new CompanyValue("Sostenibilidad operativa",
                    "Responsabilidad ambiental en cada etapa de la cadena logistica.",
                    "Inversion prioritaria en empaques reciclables y alianzas con fincas "
                            + "certificadas ambientalmente."),
            new CompanyValue("Compromiso con el cliente",
                    "Empatia, rapidez y efectividad en la resolucion de problemas y en los "
                            + "requerimientos especiales.",
                    "El equipo de soporte esta autorizado a resolver incidencias menores de forma "
                            + "inmediata en favor del cliente."),
            new CompanyValue("Innovacion constante",
                    "Tecnologia moderna para simplificar la compra de flores, tanto para hogares "
                            + "como para grandes empresas.",
                    "Desarrollo continuo de herramientas de IA y pasarelas de pago eficientes."),
            new CompanyValue("Respeto por proveedores y socios",
                    "Relaciones comerciales eticas y de largo plazo con agricultores y "
                            + "transportadores.",
                    "Pago puntual a proveedores locales y apoyo tecnico en buenas practicas "
                            + "agricolas."));

    private static final List<Objective> OBJECTIVES = List.of(
            new Objective("92%", "Experiencia del cliente",
                    "Indice de satisfaccion (CSAT) superior al 92% en todas las interacciones, "
                            + "humanas y de Florabelle."),
            new Objective("1.5%", "Calidad del producto",
                    "Reducir devoluciones y reclamos por calidad a menos del 1.5% de los despachos "
                            + "mensuales."),
            new Objective("45 min", "Operaciones",
                    "Preparar un arreglo floral complejo en menos de 45 minutos promedio desde la "
                            + "confirmacion del pedido."),
            new Objective("96%", "Logistica",
                    "Entrega a tiempo por encima del 96% en todas las zonas urbanas de cobertura."),
            new Objective("70%", "Sostenibilidad",
                    "Reducir el plastico de un solo uso en empaques en un 70% en tres anos, con "
                            + "materiales biodegradables y compostables."),
            new Objective("80%", "Tecnologia",
                    "Que Florabelle resuelva de forma autonoma al menos el 80% de las consultas "
                            + "transaccionales y de soporte basico."),
            new Objective("25%", "Crecimiento comercial",
                    "Incrementar la base de clientes mayoristas activos un 25% anual."),
            new Objective("100%", "Gestion de proveedores",
                    "Evaluar cada trimestre al 100% de los proveedores agricolas por calidad, "
                            + "cumplimiento fitosanitario y practicas laborales justas."),
            new Objective("4%", "Gestion de inventario",
                    "Mantener la merma por desecho de flor fresca por debajo del 4% mediante "
                            + "prediccion de rotacion de stock."));

    private static final List<Step> STEPS = List.of(
            new Step("Explore el catalogo",
                    "El catalogo digital muestra el inventario real, actualizado en tiempo real, "
                            + "con el precio publico de cada tallo y el estado de disponibilidad."),
            new Step("Pregunte a Florabelle",
                    "Puede describir la ocasion, pedir una recomendacion o enviarle la foto de una "
                            + "flor. Ella consulta el inventario y le responde con datos reales."),
            new Step("Reciba su cotizacion",
                    "Una cotizacion es un documento real con numero propio (COT-...), calculada por "
                            + "volumen de tallos, complejidad del diseno y estacionalidad. Vale 5 "
                            + "dias calendario."),
            new Step("Apruebe la cotizacion",
                    "Ningun pedido se procesa sin su aceptacion formal previa. Nada se cobra ni se "
                            + "confirma sin su aprobacion clara y consciente."),
            new Step("Preparacion en bodega",
                    "El pedido es oficial cuando la pasarela de pago confirma la transaccion. "
                            + "Entonces pasa a seleccion, control de calidad y diseno floral."),
            new Step("Entrega con cadena de frio",
                    "Despachamos en bloques horarios definidos, en vehiculos climatizados o con "
                            + "contenedores isotermicos, y registramos la entrega con fotografia y "
                            + "firma de quien recibe."));

    public record AvailabilityState(String code, String label, String description) {
    }

    public record CompanyValue(String name, String description, String decision) {
    }

    public record Objective(String figure, String name, String description) {
    }

    public record Step(String title, String description) {
    }
}
