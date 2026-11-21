package com.springbloom.adapter.out.agent;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
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
import com.springbloom.domain.port.in.FileComplaintUseCase;
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
    private final FileComplaintUseCase fileComplaint;
    private final PlaceOrderUseCase placeOrder;
    private final LookupOrderUseCase lookupOrder;
    private final SubmitComplaintUseCase submitComplaint;
    private final ClassifyFlowerUseCase classifyFlower;
    private final KnowledgeSearchPort knowledgeSearch;

    public SpringAiChatAgentAdapter(
            ChatClient florabelleChatClient,
            CheckAvailabilityUseCase checkAvailability,
            RequestQuotationUseCase requestQuotation,
            FileComplaintUseCase fileComplaint,
            PlaceOrderUseCase placeOrder,
            LookupOrderUseCase lookupOrder,
            SubmitComplaintUseCase submitComplaint,
            ClassifyFlowerUseCase classifyFlower,
            KnowledgeSearchPort knowledgeSearch) {

        this.chatClient = florabelleChatClient;
        this.checkAvailability = checkAvailability;
        this.requestQuotation = requestQuotation;
        this.fileComplaint = fileComplaint;
        this.placeOrder = placeOrder;
        this.lookupOrder = lookupOrder;
        this.submitComplaint = submitComplaint;
        this.classifyFlower = classifyFlower;
        this.knowledgeSearch = knowledgeSearch;
    }

    @Override
    public AgentReply reply(AgentRequest request) {

        FlorabelleTools tools = new FlorabelleTools(
                                checkAvailability, 
                                requestQuotation, 
                                fileComplaint, 
                                placeOrder, 
                                lookupOrder,
                                submitComplaint, 
                                classifyFlower, 
                                knowledgeSearch, 
                                request.sessionKey(), 
                                request.image());

        ChatResponse response = chatClient.prompt()
                .messages(toSpringAi(request.history()))
                .user(userSpec -> {
                    userSpec.text(request.customerMessage());
                    // The model sees the photo itself as well as having the tool
                    // over it: the ONNX model is the authority on which species
                    // it is, but only Claude can read the occasion, the colours
                    // and how many stems are in the picture.
                    request.attachedImage().ifPresent(image -> userSpec.media(asMedia(image)));
                })
                .tools(tools)
                .call()
                .chatResponse();

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
        TokenUsage usage = usageOf(response);
        return usage == null ? reply : reply.withUsage(usage);
    }

    /**
     * Spring AI accumulates usage across the tool round trips of one turn. Cache
     * counts only exist on Anthropic's own usage object, so they are read from it
     * when present and are zero otherwise.
     */
    private static TokenUsage usageOf(ChatResponse response) {
        if (response == null || response.getMetadata() == null || response.getMetadata().getUsage() == null) {
            return null;
        }
        Usage usage = response.getMetadata().getUsage();
        int cacheRead = 0;
        int cacheWrite = 0;
        if (usage.getNativeUsage() instanceof AnthropicApi.Usage native_) {
            cacheRead = native_.cacheReadInputTokens() == null ? 0 : native_.cacheReadInputTokens();
            cacheWrite = native_.cacheCreationInputTokens() == null ? 0 : native_.cacheCreationInputTokens();
        }
        String model = response.getMetadata().getModel();
        return new TokenUsage(
                model == null || model.isBlank() ? "unknown" : model,
                usage.getPromptTokens() == null ? 0 : usage.getPromptTokens(),
                usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens(),
                cacheRead,
                cacheWrite);
    }

    /**
     * Wraps the attachment for Spring AI. The mime type comes from the image's
     * own magic number, sniffed in ImageAttachment - Anthropic rejects a turn
     * whose declared type does not match the bytes, so a browser's guess is not
     * good enough here.
     */
    private static Media asMedia(ImageAttachment image) {
        return new Media(  // Spring AI's Media is a record, so we can use the constructor directly.
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
                .filter(message -> message.role() != MessageRole.SYSTEM)
                .<org.springframework.ai.chat.messages.Message>map(message ->
                        message.role() == MessageRole.USER
                                ? new UserMessage(message.content())
                                : new AssistantMessage(message.content()))
                .toList();
    }
}
