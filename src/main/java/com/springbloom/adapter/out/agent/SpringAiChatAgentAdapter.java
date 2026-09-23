package com.springbloom.adapter.out.agent;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;

import com.springbloom.domain.model.MessageRole;
import com.springbloom.domain.model.vo.ImageAttachment;
import com.springbloom.domain.port.in.CheckAvailabilityUseCase;
import com.springbloom.domain.port.in.ClassifyFlowerUseCase;
import com.springbloom.domain.port.in.FileComplaintUseCase;
import com.springbloom.domain.port.in.RequestQuotationUseCase;
import com.springbloom.domain.port.out.ChatAgentPort;
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
    private final ClassifyFlowerUseCase classifyFlower;
    private final KnowledgeSearchPort knowledgeSearch;

    public SpringAiChatAgentAdapter(
            ChatClient florabelleChatClient,
            CheckAvailabilityUseCase checkAvailability,
            RequestQuotationUseCase requestQuotation,
            FileComplaintUseCase fileComplaint,
            ClassifyFlowerUseCase classifyFlower,
            KnowledgeSearchPort knowledgeSearch) {

        this.chatClient = florabelleChatClient;
        this.checkAvailability = checkAvailability;
        this.requestQuotation = requestQuotation;
        this.fileComplaint = fileComplaint;
        this.classifyFlower = classifyFlower;
        this.knowledgeSearch = knowledgeSearch;
    }

    @Override
    public AgentReply reply(AgentRequest request) {
        FlorabelleTools tools = new FlorabelleTools(
                checkAvailability, requestQuotation, fileComplaint, classifyFlower,
                knowledgeSearch, request.sessionKey(), request.image());

        String answer = chatClient.prompt()
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
                .content();

        if (answer == null || answer.isBlank()) {
            log.warn("Session {} got an empty answer from the model", request.sessionKey());
            answer = "Disculpe, no pude procesar su mensaje. Podria repetirlo?";
        }

        return new AgentReply(answer, tools.lastQuotationNumber(), tools.lastComplaintNumber(),
                tools.lastVision());
    }

    /**
     * Wraps the attachment for Spring AI. The mime type comes from the image's
     * own magic number, sniffed in ImageAttachment - Anthropic rejects a turn
     * whose declared type does not match the bytes, so a browser's guess is not
     * good enough here.
     */
    private static Media asMedia(ImageAttachment image) {
        return new Media(
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
