package com.springbloom.adapter.in.chat;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Base64;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.springbloom.domain.model.FlowerSpecies;
import com.springbloom.domain.model.FlowerStock;
import com.springbloom.domain.model.ProductType;
import com.springbloom.domain.model.Quotation;
import com.springbloom.domain.model.QuotationItem;
import com.springbloom.domain.model.QuotationItemSpecies;
import com.springbloom.domain.model.VisionEvidence;
import com.springbloom.domain.model.vo.ImageAttachment;
import com.springbloom.domain.port.in.ChatUseCase;
import com.springbloom.domain.model.Message;
import com.springbloom.domain.port.in.ChatUseCase.ChatCommand;
import com.springbloom.domain.port.in.ChatUseCase.ChatHistory;
import com.springbloom.domain.port.in.ChatUseCase.ChatTurn;
import com.springbloom.domain.port.in.ClassifyFlowerUseCase;
import com.springbloom.domain.port.in.ClassifyFlowerUseCase.ClassifyFlowerCommand;
import com.springbloom.domain.port.in.ClassifyFlowerUseCase.IdentifiedFlower;
import com.springbloom.domain.port.in.RequestQuotationUseCase;
import com.springbloom.domain.port.in.RequestQuotationUseCase.LineRequest;
import com.springbloom.domain.port.in.RequestQuotationUseCase.RequestQuotationCommand;
import com.springbloom.domain.port.in.RequestQuotationUseCase.SpeciesRequest;
import com.springbloom.domain.service.UnavailableSpeciesException;
import com.springbloom.domain.service.UnknownSpeciesException;

import lombok.RequiredArgsConstructor;

/** Public chat endpoints. Anonymous: callers identify themselves with a session key. */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ClassifyFlowerUseCase classifyFlower;
    private final RequestQuotationUseCase requestQuotation;
    private final ChatUseCase chat;

    @PostMapping(path = "/identify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public IdentificationResponse identify(
            @RequestParam("image") MultipartFile image,
            @RequestParam("sessionKey") String sessionKey) {

        Optional<IdentifiedFlower> identified =
                classifyFlower.identify(new ClassifyFlowerCommand(read(image), sessionKey));

        return identified
                .map(IdentificationResponse::recognized)
                .orElseGet(IdentificationResponse::unrecognized);
    }

    /**
     * The quotation is priced and persisted before it is returned, so the number
     * in the response is one the customer can quote back.
     */
    @PostMapping(path = "/quotations", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public QuotationResponse requestQuotation(@RequestBody QuotationRequest request) {
        return QuotationResponse.of(requestQuotation.requestQuotation(request.toCommand()));
    }

    /**
     * One turn with Florabelle. Both halves are persisted before the reply
     * returns, so a refresh does not lose the conversation.
     */
    @PostMapping(path = "/messages", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ChatResponse say(@RequestBody ChatRequest request) {
        ChatTurn turn = chat.say(new ChatCommand(
                request.sessionKey(), request.message(), request.attachment()));

        return new ChatResponse(turn.conversationId(), turn.reply(),
                turn.quotationNumber(), turn.complaintNumber(),
                VisionBadge.of(turn.vision()));
    }

    /**
     * The panel rebuilds itself from this on every page load, so navigating away
     * mid-conversation does not lose it. An unknown session key is an empty
     * history, not a 404: a first-time visitor is not an error.
     */
    @GetMapping(path = "/history", produces = MediaType.APPLICATION_JSON_VALUE)
    public HistoryResponse history(
            @RequestParam("sessionKey") String sessionKey,
            @RequestParam(name = "limit", defaultValue = "40") int limit) {

        ChatHistory history = chat.history(sessionKey, limit);
        return new HistoryResponse(
                history.conversationId(),
                history.messages().stream().map(HistoryMessage::of).toList());
    }

    /**
     * image is the photo the customer dropped on the panel, base64 encoded.
     * Either a bare base64 payload or a whole data URL, because that is what
     * FileReader.readAsDataURL hands the browser and stripping it there would
     * be one more thing the client could get wrong.
     *
     * It rides in the JSON body rather than as a second multipart endpoint so
     * one turn stays one request: a photo and the words about it are the same
     * turn, and splitting them would let one arrive without the other.
     */
    public record ChatRequest(String sessionKey, String message, String image) {

        private static final String DATA_URL_PREFIX = "data:";

        /**
         * The attachment, or null when the turn is words only.
         *
         * The declared type in a data URL is deliberately discarded:
         * ImageAttachment decides from the bytes, so a .png renamed .jpg is
         * still sent to the model as the PNG it actually is.
         */
        ImageAttachment attachment() {
            if (image == null || image.isBlank()) {
                return null;
            }
            String payload = image.trim();
            if (payload.startsWith(DATA_URL_PREFIX)) {
                int comma = payload.indexOf(',');
                if (comma < 0) {
                    throw new IllegalArgumentException("That image could not be read");
                }
                payload = payload.substring(comma + 1);
            }
            try {
                return ImageAttachment.of(Base64.getMimeDecoder().decode(payload));
            } catch (IllegalArgumentException notBase64) {
                throw new IllegalArgumentException(
                        "That image could not be read: " + notBase64.getMessage(), notBase64);
            }
        }
    }

    public record HistoryResponse(UUID conversationId, List<HistoryMessage> messages) {
    }

    /** role is USER or ASSISTANT; the panel renders them as customer and agent. */
    public record HistoryMessage(String role, String content, Instant createdAt) {

        static HistoryMessage of(Message message) {
            return new HistoryMessage(
                    message.role().name(), message.content(), message.createdAt());
        }
    }

    /** Each number is null unless this turn actually produced that document. */
    public record ChatResponse(
            UUID conversationId,
            String reply,
            String quotationNumber,
            String complaintNumber,
            VisionBadge vision) {
    }

    /**
     * What the trained model reported, for the panel to render as provenance.
     * Null on a turn with no photo.
     *
     * It is a separate wire record rather than the domain's VisionEvidence for
     * the same reason the quotation DTOs are: the wire format is the adapter's
     * to change, and confidencePercent is sent as a number the UI prints, never
     * as a decimal it would have to format itself.
     */
    public record VisionBadge(
            String model,
            String decidedBy,
            String speciesKey,
            String commonName,
            String scientificName,
            int confidencePercent,
            boolean trusted) {

        static VisionBadge of(VisionEvidence evidence) {
            return evidence == null ? null : new VisionBadge(
                    evidence.modelName(),
                    evidence.decidedBy().name(),
                    evidence.speciesKey(),
                    evidence.commonName(),
                    evidence.scientificName(),
                    evidence.confidencePercent(),
                    evidence.trusted());
        }
    }

    private byte[] read(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new IllegalArgumentException("An image file is required");
        }
        try {
            return image.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read the uploaded image", e);
        }
    }

    /**
     * An unrecognized photo is a normal outcome, not an error: the agent needs
     * to answer the customer either way, so it comes back 200 with a flag.
     */
    public record IdentificationResponse(
            boolean recognized,
            Long speciesId,
            String speciesKey,
            String commonName,
            String scientificName,
            Double confidence,
            String status,
            String availability,
            boolean quotable,
            boolean availableImmediately,
            String unitPrice,
            Integer etaDays) {

        static IdentificationResponse recognized(IdentifiedFlower flower) {
            FlowerSpecies species = flower.species();
            return new IdentificationResponse(
                    true,
                    species.getId(),
                    species.getSpeciesKey(),
                    species.getCommonName(),
                    species.getScientificName(),
                    flower.confidence(),
                    flower.stock().map(stock -> stock.getStatus().name()).orElse(null),
                    flower.stock().map(stock -> stock.getStatus().label()).orElse(null),
                    flower.quotable(),
                    flower.stock().map(FlowerStock::availableImmediately).orElse(false),
                    flower.unitPrice().map(price -> price.amount().toPlainString()).orElse(null),
                    flower.stock().flatMap(FlowerStock::etaDays).orElse(null));
        }

        static IdentificationResponse unrecognized() {
            return new IdentificationResponse(
                    false, null, null, null, null, null, null, null, false, false, null, null);
        }
    }

    /**
     * Mirrors RequestQuotationCommand rather than reusing it: the wire format is
     * the adapter's to change, and a domain record should not carry Jackson's
     * constraints. Validation stays in the command, built here.
     */
    public record QuotationRequest(String sessionKey, List<LineBody> lines) {

        RequestQuotationCommand toCommand() {
            if (lines == null || lines.isEmpty()) {
                throw new IllegalArgumentException("A quotation needs at least one line");
            }
            return new RequestQuotationCommand(sessionKey, lines.stream()
                    .map(LineBody::toLineRequest)
                    .toList());
        }
    }

    /** discountPercentage is null for INDIVIDUAL and required for BOUQUET/GARLAND. */
    public record LineBody(
            ProductType productType,
            BigDecimal discountPercentage,
            List<SpeciesBody> species) {

        LineRequest toLineRequest() {
            if (species == null || species.isEmpty()) {
                throw new IllegalArgumentException("A quotation line needs at least one species");
            }
            return new LineRequest(productType, discountPercentage, species.stream()
                    .map(SpeciesBody::toSpeciesRequest)
                    .toList());
        }
    }

    public record SpeciesBody(String speciesKey, Integer quantity) {

        SpeciesRequest toSpeciesRequest() {
            if (quantity == null) {
                throw new IllegalArgumentException("quantity is required for " + speciesKey);
            }
            return new SpeciesRequest(speciesKey, quantity);
        }
    }

    /** Money goes out as plain strings: a JSON number would reintroduce binary floats. */
    public record QuotationResponse(
            Long id,
            String quotationNumber,
            String status,
            Instant validUntil,
            String subtotal,
            String discountAmount,
            String totalAmount,
            int totalStems,
            List<LineResponse> lines) {

        static QuotationResponse of(Quotation quotation) {
            return new QuotationResponse(
                    quotation.id(),
                    quotation.quotationNumber(),
                    quotation.status().name(),
                    quotation.validUntil(),
                    quotation.subtotal().amount().toPlainString(),
                    quotation.discountAmount().amount().toPlainString(),
                    quotation.totalAmount().amount().toPlainString(),
                    quotation.totalStems(),
                    quotation.items().stream().map(LineResponse::of).toList());
        }
    }

    public record LineResponse(
            String productType,
            BigDecimal discountPercentage,
            String composedSubtotal,
            String discountAmount,
            String subtotal,
            List<SpeciesLineResponse> species) {

        static LineResponse of(QuotationItem item) {
            return new LineResponse(
                    item.productType().name(),
                    item.discountPercentage(),
                    item.composedSubtotal().amount().toPlainString(),
                    item.discountAmount().amount().toPlainString(),
                    item.subtotal().amount().toPlainString(),
                    item.species().stream().map(SpeciesLineResponse::of).toList());
        }
    }

    /** The snapshots, not today's catalog: this is what the customer was quoted. */
    public record SpeciesLineResponse(
            Long speciesId,
            String commonName,
            int quantity,
            String unitPrice,
            String lineTotal) {

        static SpeciesLineResponse of(QuotationItemSpecies line) {
            return new SpeciesLineResponse(
                    line.speciesId(),
                    line.commonNameSnapshot(),
                    line.quantity(),
                    line.unitPriceSnapshot().amount().toPlainString(),
                    line.lineTotal().amount().toPlainString());
        }
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse onInvalidRequest(IllegalArgumentException e) {
        log.debug("Rejected identification request: {}", e.getMessage());
        return new ErrorResponse(e.getMessage());
    }

    /**
     * A malformed body: Jackson wraps the record's own validation, so the useful
     * message is the cause's, not the parser's.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse onUnreadableBody(HttpMessageNotReadableException e) {
        Throwable cause = e.getMostSpecificCause();
        log.debug("Rejected quotation body: {}", cause.getMessage());
        return new ErrorResponse(cause instanceof IllegalArgumentException
                ? cause.getMessage()
                : "The request body could not be read");
    }

    /** A key that is not in the catalog at all: the agent should correct it. */
    @ExceptionHandler(UnknownSpeciesException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public UnavailableResponse onUnknownSpecies(UnknownSpeciesException e) {
        log.info("Quotation asked for unknown species {}", e.getSpeciesKey());
        return new UnavailableResponse(e.getSpeciesKey(), "No conocemos esa flor");
    }

    /**
     * A real flower the customer cannot have right now. 409 rather than 400: the
     * request was well formed, the stock simply says no, and the agent is meant
     * to read the Spanish reason back and offer an alternative.
     */
    @ExceptionHandler(UnavailableSpeciesException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public UnavailableResponse onUnavailableSpecies(UnavailableSpeciesException e) {
        log.info("Quotation refused for {}: {}", e.getSpeciesKey(), e.getReason());
        return new UnavailableResponse(e.getSpeciesKey(), e.getReason());
    }

    public record ErrorResponse(String message) {
    }

    public record UnavailableResponse(String speciesKey, String reason) {
    }
}
