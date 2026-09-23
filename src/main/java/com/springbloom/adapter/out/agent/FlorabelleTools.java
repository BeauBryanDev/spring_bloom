package com.springbloom.adapter.out.agent;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import com.springbloom.domain.model.Complaint;
import com.springbloom.domain.model.ComplaintType;
import com.springbloom.domain.model.ProductType;
import com.springbloom.domain.model.Quotation;
import com.springbloom.domain.model.QuotationItem;
import com.springbloom.domain.model.VisionEvidence;
import com.springbloom.domain.model.vo.ImageAttachment;
import com.springbloom.domain.port.in.CheckAvailabilityUseCase;
import com.springbloom.domain.port.in.CheckAvailabilityUseCase.AvailableFlower;
import com.springbloom.domain.port.in.CheckAvailabilityUseCase.CheckAvailabilityCommand;
import com.springbloom.domain.port.in.ClassifyFlowerUseCase;
import com.springbloom.domain.port.in.ClassifyFlowerUseCase.ClassifyFlowerCommand;
import com.springbloom.domain.port.in.ClassifyFlowerUseCase.IdentifiedFlower;
import com.springbloom.domain.port.in.FileComplaintUseCase;
import com.springbloom.domain.port.in.FileComplaintUseCase.FileComplaintCommand;
import com.springbloom.domain.port.in.RequestQuotationUseCase;
import com.springbloom.domain.port.in.RequestQuotationUseCase.LineRequest;
import com.springbloom.domain.port.in.RequestQuotationUseCase.RequestQuotationCommand;
import com.springbloom.domain.port.in.RequestQuotationUseCase.SpeciesRequest;
import com.springbloom.domain.port.out.KnowledgeSearchPort;
import com.springbloom.domain.port.out.KnowledgeSearchPort.KnowledgePassage;
import com.springbloom.domain.port.out.KnowledgeSearchPort.KnowledgeQuery;
import com.springbloom.domain.port.out.KnowledgeSearchPort.Scope;
import com.springbloom.domain.service.UnavailableSpeciesException;
import com.springbloom.domain.service.UnknownSpeciesException;

/**
 * The agent's tools: thin wrappers over the use cases that already exist, so
 * the model orchestrates the backend rather than reimplementing it.
 *
 * One instance per request, constructed with the session key. The key is never
 * a tool parameter - a model that could name a session could raise a quotation
 * against someone else's conversation.
 *
 * Every tool returns a record rather than prose. Spring AI serializes it to
 * JSON, so prices reach the model as data it must relay, not as a sentence it
 * is tempted to rewrite.
 */
public class FlorabelleTools {

    private static final Logger log = LoggerFactory.getLogger(FlorabelleTools.class);
    private static final int MAX_ALTERNATIVES = 4;

    private final CheckAvailabilityUseCase checkAvailability;
    private final RequestQuotationUseCase requestQuotation;
    private final FileComplaintUseCase fileComplaint;
    private final ClassifyFlowerUseCase classifyFlower;
    private final KnowledgeSearchPort knowledgeSearch;
    private final String sessionKey;

    /**
     * The photo attached to this turn, or null. Like the session key it is bound
     * in the constructor and is never a tool parameter: an image is megabytes of
     * base64 that would have to survive a round trip through the model to come
     * back unchanged, and a model that could name an image could ask about one
     * that is not this customer's.
     */
    private final ImageAttachment image;

    /** Records the last quotation raised this turn, for the reply to link to. */
    private final AtomicReference<String> lastQuotationNumber = new AtomicReference<>();

    /** Same, for a claim filed this turn. */
    private final AtomicReference<String> lastComplaintNumber = new AtomicReference<>();

    /**
     * What the trained model reported this turn, so the reply can carry it as
     * evidence instead of the UI having to trust the agent's prose for it.
     */
    private final AtomicReference<VisionEvidence> lastVision = new AtomicReference<>();

    public FlorabelleTools(
            CheckAvailabilityUseCase checkAvailability,
            RequestQuotationUseCase requestQuotation,
            FileComplaintUseCase fileComplaint,
            ClassifyFlowerUseCase classifyFlower,
            KnowledgeSearchPort knowledgeSearch,
            String sessionKey,
            ImageAttachment image) {

        this.checkAvailability = checkAvailability;
        this.requestQuotation = requestQuotation;
        this.fileComplaint = fileComplaint;
        this.classifyFlower = classifyFlower;
        this.knowledgeSearch = knowledgeSearch;
        this.sessionKey = sessionKey;
        this.image = image;
    }

    public String lastQuotationNumber() {
        return lastQuotationNumber.get();
    }

    public String lastComplaintNumber() {
        return lastComplaintNumber.get();
    }

    public VisionEvidence lastVision() {
        return lastVision.get();
    }

    @Tool(description = """
            Look up which flowers the shop has, their real price in Colombian pesos and \
            whether they can be sold right now. Call this before ever mentioning a price. \
            Accepts what the customer typed, in Spanish, such as "rosas" or "claveles".""")
    public AvailabilityAnswer findFlowers(
            @ToolParam(description = "The flower the customer named, in their own words")
            String flowerName,
            @ToolParam(required = false, description = "How many stems they asked about, if they said")
            Integer quantity) {

        List<FlowerOption> options = checkAvailability
                .check(new CheckAvailabilityCommand(flowerName, quantity, MAX_ALTERNATIVES))
                .stream()
                .map(FlowerOption::of)
                .toList();

        log.info("Session {} searched '{}': {} options", sessionKey, flowerName, options.size());
        return new AvailabilityAnswer(flowerName, options.isEmpty(), options);
    }

    @Tool(description = """
            Raise a real, saved quotation for a single kind of flower and return its \
            quotation number and total. Only call this once the customer has agreed on \
            the flower and how many stems they want.""")
    public QuotationAnswer quoteSingleFlower(
            @ToolParam(description = "The flower to quote, as findFlowers reported it")
            String flowerName,
            @ToolParam(description = "How many stems") int quantity) {

        return quoting(() -> List.of(new LineRequest(ProductType.INDIVIDUAL, null,
                List.of(new SpeciesRequest(resolveKey(flowerName), quantity)))));
    }

    /**
     * The discount is deliberately NOT a parameter. It is a commercial term the
     * shop decides from the stem count, and asking the model for one produced
     * exactly the failure it invited: told never to invent a price, Florabelle
     * asked the customer what discount they would like. BouquetDiscountPolicy
     * answers it now, and the tool cannot express the question.
     */
    @Tool(description = """
            Raise a real, saved quotation for a bouquet built from several kinds of \
            flower, and return its quotation number and total. The shop's volume \
            discount is applied automatically from the number of stems - never ask \
            the customer what discount they want, and never offer one yourself. The \
            answer tells you the percentage that was applied.""")
    public QuotationAnswer quoteBouquet(
            @ToolParam(description = "Each flower in the bouquet and how many stems of it")
            List<BouquetLine> flowers) {

        return quoting(() -> List.of(new LineRequest(ProductType.BOUQUET, null,
                flowers.stream()
                        .map(line -> new SpeciesRequest(resolveKey(line.flowerName()), line.quantity()))
                        .toList())));
    }


    @Tool(description = """
            Identify the flower in the photo the customer just attached to this \
            conversation, using the shop's own flower recognition model, and return \
            its species, the real price in Colombian pesos and whether it can be sold. \
            Call this whenever the customer has sent a photo and wants to know what \
            the flower is, what it costs, or whether the shop has it. The photo is \
            already attached: it is not something you pass in. If no photo was sent, \
            this reports imageAttached false - ask the customer to send one.""")
    public PhotoAnswer identifyFlowerInPhoto() {

        if (image == null) {
            log.info("Session {} asked to identify a photo with none attached", sessionKey);
            return PhotoAnswer.noImage();
        }

        try {
            return classifyFlower
                    .identify(new ClassifyFlowerCommand(image.bytes(), sessionKey))
                    .map(flower -> {
                        log.info("Session {} photo identified as {} at {}",
                                sessionKey, flower.speciesKey(), flower.confidence());

                        lastVision.set(VisionEvidence.of(
                                flower.speciesKey(),
                                flower.species().getCommonName(),
                                flower.species().getScientificName(),
                                flower.confidencePercent(),
                                flower.trusted()));

                        return PhotoAnswer.identified(flower);
                    })
                    .orElseGet(() -> {
                        log.info("Session {} sent a photo nothing was recognized in", sessionKey);
                        lastVision.set(VisionEvidence.nothingDetected());
                        return PhotoAnswer.unrecognized();
                    });

        } catch (IllegalArgumentException unusable) {
            log.warn("Session {} sent an unusable photo: {}", sessionKey, unusable.getMessage());
            return PhotoAnswer.unusable(unusable.getMessage());
        }
    }

    @Tool(description = """
            Record a customer complaint about an order that already happened: flowers \
            that arrived damaged, a late or missing delivery, or the wrong item. Returns \
            the complaint number the customer can quote when following it up. Filing a \
            complaint does NOT grant a refund or a replacement - the shop decides that \
            afterwards.""")
    public ComplaintAnswer fileComplaint(
            @ToolParam(description = "What went wrong: DAMAGED_FLOWERS, LATE_DELIVERY, "
                    + "NOT_DELIVERED, WRONG_ITEM or OTHER")
            ComplaintType type,
            @ToolParam(description = "What the customer described, in their own words, in Spanish")
            String description,
            @ToolParam(required = false,
                    description = "The ORD-... order number, only if the customer gave one")
            String orderNumber) {

        try {
            Complaint filed = fileComplaint.file(
                    new FileComplaintCommand(sessionKey, type, description, orderNumber));

            lastComplaintNumber.set(filed.complaintNumber());
            log.info("Session {} filed complaint {}", sessionKey, filed.complaintNumber());

            return new ComplaintAnswer(true, filed.complaintNumber(), filed.type().label(),
                    filed.status().label(), filed.order().isPresent(), null);

        } catch (IllegalArgumentException invalid) {
            log.warn("Session {} filed an invalid complaint: {}", sessionKey, invalid.getMessage());
            return new ComplaintAnswer(false, null, null, null, false, invalid.getMessage());
        }
    }

    @Tool(description = """
            Look up what Spring-Bloom's own written policies say: delivery, coverage and \
            delivery windows, refunds for damaged flowers, cancellations, imports, \
            payment methods, quality standards, how quotations work, and the Colombian \
            and world flower markets. Call this before answering any question about what \
            the shop does, promises or charges for. Never answer such a question from \
            memory. If it returns nothing, say you will check with the team rather than \
            inventing a policy.""")
    public KnowledgeAnswer searchPolicy(
            @ToolParam(description = "The customer's question, in Spanish, in their own words")
            String question) {

        return answering(question, Scope.COMMERCIAL);
    }

    @Tool(description = """
            Look up how a plant or flower actually works in the shop's botany reference: \
            anatomy, tissues, roots, stems, leaves, flower and fruit structure. Use this \
            only for questions about botany itself. It knows nothing about prices, stock \
            or shop policy - use searchPolicy or findFlowers for those.""")
    public KnowledgeAnswer explainBotany(
            @ToolParam(description = "The botanical question, in Spanish")
            String question) {

        return answering(question, Scope.BOTANICAL);
    }

    /**
     * Finding nothing is an answer, not an error. An empty passage list reaches
     * the model as found=false, which the prompt tells it to relay as "I will
     * check" - the alternative, a low-scoring passage, is an irrelevant policy
     * presented as a relevant one.
     */
    private KnowledgeAnswer answering(String question, Scope scope) {
        try {
            List<Passage> passages = knowledgeSearch
                    .search(KnowledgeQuery.of(question, scope))
                    .stream()
                    .map(Passage::of)
                    .toList();

            log.info("Session {} searched {} knowledge for '{}': {} passages",
                    sessionKey, scope, question, passages.size());

            return new KnowledgeAnswer(!passages.isEmpty(), passages);

        } catch (IllegalArgumentException invalid) {
            log.warn("Session {} asked an unusable knowledge question: {}", sessionKey, invalid.getMessage());
            return new KnowledgeAnswer(false, List.of());
        }
    }

    /**
     * A flower the customer cannot have is a business answer, not a failure, so
     * it comes back as a result the model can relay. Letting the exception out
     * would surface to the model as a tool error and invite it to invent a
     * consolation price.
     */
    private QuotationAnswer quoting(Supplier<List<LineRequest>> buildLines) {
        try {
            Quotation saved = requestQuotation.requestQuotation(
                    new RequestQuotationCommand(sessionKey, buildLines.get()));

            lastQuotationNumber.set(saved.quotationNumber());
            log.info("Session {} raised quotation {}", sessionKey, saved.quotationNumber());

            return QuotationAnswer.raised(saved);

        } catch (UnavailableSpeciesException unavailable) {
            log.info("Session {} refused {}: {}",
                    sessionKey, unavailable.getSpeciesKey(), unavailable.getReason());
            return QuotationAnswer.refused(unavailable.getSpeciesKey(), unavailable.getReason());

        } catch (UnknownSpeciesException unknown) {
            log.warn("Session {} quoted unresolvable key {}", sessionKey, unknown.getSpeciesKey());
            return QuotationAnswer.refused(unknown.getSpeciesKey(), "no esta en el catalogo");

        } catch (IllegalArgumentException invalid) {
            log.warn("Session {} asked for an impossible quotation: {}", sessionKey, invalid.getMessage());
            return QuotationAnswer.refused(null, invalid.getMessage());
        }
    }

    /**
     * Turns what the model typed into a species_key the domain will accept.
     *
     * The model talks to customers, so it says "rosa clasica", not "rose", and
     * RequestQuotationCommand matches species_key exactly. Translating here is
     * the whole job of a tool boundary - requiring the model to carry an exact
     * machine key across turns is a bug waiting for the turn it forgets.
     */
    private String resolveKey(String flowerName) {
        return checkAvailability.check(CheckAvailabilityCommand.of(flowerName)).stream()
                .findFirst()
                .map(AvailableFlower::speciesKey)
                .orElseThrow(() -> new UnknownSpeciesException(flowerName));
    }

    /**
     * orderIdentified says whether we could match the order number. False is not
     * a failure: the claim is recorded either way, and the shop can chase it.
     */
    public record ComplaintAnswer(
            boolean filed,
            String complaintNumber,
            String type,
            String status,
            boolean orderIdentified,
            String problem) {
    }

    /**
     * The passages themselves, not a summary of them: the model must quote what
     * the manual says rather than paraphrase it into a new promise.
     */
    public record KnowledgeAnswer(boolean found, List<Passage> passages) {
    }

    public record Passage(String text, String fromDocument, String section) {

        static Passage of(KnowledgePassage passage) {
            return new Passage(passage.content(), passage.documentTitle(), passage.section());
        }
    }

    public record BouquetLine(
            @ToolParam(description = "The flower's name") String flowerName,
            @ToolParam(description = "How many stems of it") int quantity) {
    }

    public record AvailabilityAnswer(String searchedFor, boolean nothingFound, List<FlowerOption> options) {
    }

    /**
     * unitPriceCop is null exactly when the flower may not be sold, so there is
     * no number for the model to read out by mistake.
     */
    public record FlowerOption(
            String speciesKey,
            String commonName,
            String availability,
            boolean canBeSold,
            boolean availableNow,
            String unitPriceCop,
            Integer etaDays,
            boolean enoughForWhatTheyAsked) {

        static FlowerOption of(AvailableFlower flower) {
            return new FlowerOption(
                    flower.speciesKey(),
                    flower.commonName(),
                    flower.availability(),
                    flower.quotable(),
                    flower.availableImmediately(),
                    flower.unitPrice().map(price -> price.amount().toPlainString()).orElse(null),
                    flower.etaDays().orElse(null),
                    flower.fulfilsRequest());
        }
    }

    /**
     * What the trained YOLO model saw, as the agent receives it.
     *
     * The bands are decided in ClassifyFlowerService, not here and not in the
     * prompt: a prompt rule saying "only overrule below 0.5" is a suggestion a
     * model can ignore, while a record that simply does not contain an
     * alternative is not something it can disobey.
     *
     * When the model is certain, the species and its price are both present and
     * mayUseOwnJudgement is false - there is nothing else to reach for. When it
     * is not, the species comes back as a suggestion with NO price: whatever
     * flower the agent then settles on, it has to price through findFlowers,
     * so a price still only ever comes from the database.
     */
    public record PhotoAnswer(
            boolean imageAttached,
            boolean recognized,
            String identifiedBy,
            String speciesKey,
            String commonName,
            String scientificName,
            Integer confidencePercent,
            boolean modelIsCertain,
            boolean mayUseOwnJudgement,
            String availability,
            boolean canBeSold,
            boolean availableNow,
            String unitPriceCop,
            Integer etaDays,
            String problem) {

        /** Named in the payload so the agent never presents the species as its own guess. */
        private static final String TRAINED_MODEL = "Modelo YOLOv11s-seg entrenado por Springbloom";

        static PhotoAnswer identified(IdentifiedFlower flower) {
            boolean certain = flower.trusted();

            return new PhotoAnswer(
                    true,
                    true,
                    TRAINED_MODEL,
                    flower.speciesKey(),
                    flower.species().getCommonName(),
                    flower.species().getScientificName(),
                    flower.confidencePercent(),
                    certain,
                    !certain,
                    certain ? flower.stock().map(stock -> stock.getStatus().label()).orElse(null) : null,
                    certain && flower.quotable(),
                    certain && flower.stock().map(stock -> stock.availableImmediately()).orElse(false),
                    certain
                            ? flower.unitPrice().map(price -> price.amount().toPlainString()).orElse(null)
                            : null,
                    certain ? flower.stock().flatMap(stock -> stock.etaDays()).orElse(null) : null,
                    certain ? null
                            : "El modelo no esta seguro. Mire usted la foto y proponga la flor; "
                                    + "confirme con el cliente y use findFlowers antes de dar un precio.");
        }

        static PhotoAnswer unrecognized() {
            return new PhotoAnswer(true, false, TRAINED_MODEL, null, null, null, null,
                    false, true, null, false, false, null, null,
                    "El modelo no reconocio ninguna flor en la foto");
        }

        static PhotoAnswer noImage() {
            return new PhotoAnswer(false, false, null, null, null, null, null,
                    false, false, null, false, false, null, null,
                    "El cliente no ha enviado ninguna foto");
        }

        static PhotoAnswer unusable(String problem) {
            return new PhotoAnswer(true, false, TRAINED_MODEL, null, null, null, null,
                    false, false, null, false, false, null, null, problem);
        }
    }

    /**
     * discountPercentage is the figure the shop's policy applied, reported back
     * so the agent can state it. It is an outcome to relay, never an input: the
     * model has no way to choose it.
     */
    public record QuotationAnswer(
            boolean quoted,
            String quotationNumber,
            String totalCop,
            String subtotalCop,
            String discountCop,
            String discountPercentage,
            Integer totalStems,
            String refusedSpecies,
            String reasonInSpanish) {

        static QuotationAnswer raised(Quotation quotation) {
            return new QuotationAnswer(
                    true,
                    quotation.quotationNumber(),
                    quotation.totalAmount().amount().toPlainString(),
                    quotation.subtotal().amount().toPlainString(),
                    quotation.discountAmount().amount().toPlainString(),
                    appliedDiscount(quotation),
                    quotation.totalStems(),
                    null, null);
        }

        /** The percentage on the first discounted line, or null when nothing was discounted. */
        private static String appliedDiscount(Quotation quotation) {
            return quotation.items().stream()
                    .map(QuotationItem::discountPercentage)
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .map(BigDecimal::toPlainString)
                    .orElse(null);
        }

        static QuotationAnswer refused(String speciesKey, String reason) {
            return new QuotationAnswer(
                    false, null, null, null, null, null, null, speciesKey, reason);
        }
    }
}
