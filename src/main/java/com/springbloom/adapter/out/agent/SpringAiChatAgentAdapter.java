package com.springbloom.adapter.out.agent;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import com.springbloom.config.GuardrailProperties;
import com.springbloom.domain.service.AgentUnavailableException;
import java.util.Arrays;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;

import com.springbloom.domain.model.MessageRole;
import com.springbloom.domain.model.TokenUsage;
import com.springbloom.domain.model.vo.ImageAttachment;
import com.springbloom.domain.port.in.CheckAvailabilityUseCase;
import com.springbloom.domain.port.in.ClassifyFlowerUseCase;
import com.springbloom.domain.port.in.ListCreationsUseCase;
import com.springbloom.domain.port.in.LookupOrderUseCase;
import com.springbloom.domain.port.in.PlaceOrderUseCase;
import com.springbloom.domain.port.in.RequestQuotationUseCase;
import com.springbloom.domain.port.out.ChatAgentPort;
import com.springbloom.domain.port.in.SubmitComplaintUseCase;
import com.springbloom.domain.port.out.KnowledgeSearchPort;

/**
 * Drives Claude through Spring AI. The tools are built fresh for each request
 * and bound to that request's session, so nothing about one conversation can
 * leak into another.
 */
@Component
public class SpringAiChatAgentAdapter implements ChatAgentPort {

    private static final Logger log = LoggerFactory.getLogger(SpringAiChatAgentAdapter.class);

    private final ChatClient chatClient;
    private final CheckAvailabilityUseCase checkAvailability;
    private final RequestQuotationUseCase requestQuotation;
    private final PlaceOrderUseCase placeOrder;
    private final LookupOrderUseCase lookupOrder;
    private final SubmitComplaintUseCase submitComplaint;
    private final ClassifyFlowerUseCase classifyFlower;
    private final KnowledgeSearchPort knowledgeSearch;
    private final ListCreationsUseCase listCreations;
    private final GuardrailProperties guardrails;

    public SpringAiChatAgentAdapter(
            ChatClient florabelleChatClient,
            CheckAvailabilityUseCase checkAvailability,
            RequestQuotationUseCase requestQuotation,
            PlaceOrderUseCase placeOrder,
            LookupOrderUseCase lookupOrder,
            SubmitComplaintUseCase submitComplaint,
            ClassifyFlowerUseCase classifyFlower,
            KnowledgeSearchPort knowledgeSearch,
            ListCreationsUseCase listCreations,
            GuardrailProperties guardrails) {

        this.guardrails = guardrails;
        this.chatClient = florabelleChatClient;
        this.checkAvailability = checkAvailability;
        this.requestQuotation = requestQuotation;
        this.placeOrder = placeOrder;
        this.lookupOrder = lookupOrder;
        this.submitComplaint = submitComplaint;
        this.classifyFlower = classifyFlower;
        this.knowledgeSearch = knowledgeSearch;
        this.listCreations = listCreations;
    }

    @Override
    public AgentReply reply(AgentRequest request) {

        FlorabelleTools tools = new FlorabelleTools(
                checkAvailability,
                requestQuotation,
                placeOrder,
                lookupOrder,
                submitComplaint,
                classifyFlower,
                knowledgeSearch,
                listCreations,
                request.sessionKey(),
                request.image(),
                request.clientIp());

        TurnToolGuard toolGuard = new TurnToolGuard(guardrails.getMaxToolCalls());
        ToolCallback[] guarded = Arrays.stream(ToolCallbacks.from(tools))
                .map(callback -> (ToolCallback) new GuardedToolCallback(callback, toolGuard))
                .toArray(ToolCallback[]::new);

        TurnUsageCollector.begin();
        ChatResponse response;
        try {
            response = chatClient.prompt()
                    .messages(toSpringAi(request.history()))
                    .user(userSpec -> {
                        userSpec.text(request.customerMessage());
                        // The model sees the photo itself as well as having the tool
                        // over it: the ONNX model is the authority on which species
                        // it is, but only Claude can read the occasion, the colours
                        // and how many stems are in the picture.
                        request.attachedImage().ifPresent(image -> userSpec.media(asMedia(image)));
                    })
                    .toolCallbacks(guarded)
                    .call()
                    .chatResponse();
        } catch (RuntimeException failure) {
            TurnUsageCollector.end();
            log.error("Session {} the model call failed", request.sessionKey(), failure);
            throw new AgentUnavailableException(failure);
        }
        Optional<TokenUsage> measured = TurnUsageCollector.end();

        String answer = response == null || response.getResult() == null
                ? null
                : response.getResult().getOutput().getText();

        if (answer == null || answer.isBlank()) {
            log.warn("Session {} got an empty answer from the model", request.sessionKey());
            answer = "Disculpe, no pude procesar su mensaje. Podria repetirlo?";
        }

        AgentReply reply = new AgentReply(answer, tools.lastQuotationNumber(),
                tools.lastComplaintNumber(),
                tools.lastOrderNumber(),
                tools.lastVision());
        reply = reply.withFacts(toolGuard.facts()
                .withDocumentNumber(tools.lastQuotationNumber())
                .withDocumentNumber(tools.lastComplaintNumber())
                .withDocumentNumber(tools.lastOrderNumber()));

        return measured.map(reply::withUsage).orElse(reply);
    }

    /**
     * Wraps the attachment for Spring AI. The mime type comes from the image's
     * own magic number, sniffed in ImageAttachment - Anthropic rejects a turn
     * whose declared type does not match the bytes, so a browser's guess is not
     * good enough here.
     */
    private static Media asMedia(ImageAttachment image) {
        return new Media( // Spring AI's Media is a record, so we can use the constructor directly.
                MimeTypeUtils.parseMimeType(image.mimeType()),
                new ByteArrayResource(image.bytes()));
    }

    /**
     * SYSTEM turns are not replayed: the system prompt is set fresh on every
     * request, and sending the stored copy as well would duplicate it.
     */
    private List<org.springframework.ai.chat.messages.Message> toSpringAi(
            List<com.springbloom.domain.model.Message> history) {

        return history.stream()
                .filter(message -> message
                        .role() != MessageRole.SYSTEM).<org.springframework.ai.chat.messages.Message>map(
                                message -> message.role() == MessageRole.USER
                                        ? new UserMessage(message.content())
                                        : new AssistantMessage(message.content()))
                .toList();
    }
}
