package com.springbloom.application.usecase;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.springbloom.config.GuardrailProperties;
import com.springbloom.domain.model.Conversation;
import com.springbloom.domain.model.GuardrailEvent.Stage;
import com.springbloom.domain.model.Message;
import com.springbloom.domain.model.MessageRole;
import com.springbloom.domain.port.in.ChatUseCase;
import com.springbloom.domain.port.out.ChatAgentPort;
import com.springbloom.domain.port.out.ChatAgentPort.AgentReply;
import com.springbloom.domain.port.out.ChatAgentPort.AgentRequest;
import com.springbloom.domain.port.out.AgentUsageRepository;
import com.springbloom.domain.port.out.ConversationRepository;
import com.springbloom.domain.port.out.GuardrailEventRepository;
import com.springbloom.domain.service.guardrail.GuardVerdict;
import com.springbloom.domain.service.guardrail.GuardrailMessages;
import com.springbloom.domain.service.guardrail.InputGuard;
import com.springbloom.domain.service.guardrail.OutputGuard;
import com.springbloom.domain.service.guardrail.TurnFacts;

import lombok.RequiredArgsConstructor;

/**
 * Remember, ask, remember. The agent is a port like any other, so this service
 * knows nothing about models or tools.
 */
@Service
@RequiredArgsConstructor
public class ChatService implements ChatUseCase {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    /** How many past turns to replay. Bounded because a context window is. */
    private static final int HISTORY_TURNS = 20;

    private final ConversationRepository conversations;
    private final ChatAgentPort agent;
    private final AgentUsageRepository usageRepository;
    private final GuardrailEventRepository guardrailEvents;
    private final GuardrailProperties guardrails;

    /**
     * Reading history must never start a conversation: a page load is not a
     * turn, and inserting a row for every visitor who never types would fill
     * the table with empty sessions.
     */
    @Override
    public ChatHistory history(String sessionKey, int limit) {

        if (sessionKey == null || sessionKey.isBlank()) {
                
            throw new IllegalArgumentException("A session key is required");
        }
        int bounded = Math.min(Math.max(limit, 1), ChatHistory.MAX_TURNS);

        return conversations.findBySessionKey(sessionKey)
                .map(conversation -> new ChatHistory(
                        conversation.id(),
                        conversation.withMessages(
                                        conversations.history(conversation.id(), bounded))
                                .customerVisibleMessages()))
                .orElseGet(() -> new ChatHistory(null, List.of()));
    }

    @Override
    public ChatTurn say(ChatCommand command) {

        Conversation conversation = conversations.findOrStart(command.sessionKey());
        UUID conversationId = conversation.id();

        // Read history before appending, so the new turn is not replayed twice:
        // it is passed to the agent separately as the message being answered.
        List<Message> history = conversations.history(conversationId, HISTORY_TURNS);

        GuardVerdict inbound = guardrails.isEnabled()
                ? InputGuard.check(command.message())
                : GuardVerdict.pass();

        if (!inbound.passed()) {
            return refuse(command, conversationId, inbound);
        }

        conversations.append(conversationId, MessageRole.USER, remembered(command));

        AgentReply reply = agent.reply(new AgentRequest(
                command.sessionKey(), history, command.message(), command.image(), command.clientIp()
                                                        ));

        String text = reply.text();
        if (guardrails.isEnabled()) {
            TurnFacts known = TurnFacts.harvest(texts(history, command.message())).with(reply.facts());
            GuardVerdict outbound = OutputGuard.check(text, known);

            if (!outbound.passed()) {
                log.warn("Session {} reply replaced, rule {}", command.sessionKey(), outbound.rule());
                recordEvent(conversationId, Stage.OUTPUT, outbound.rule());
                text = GuardrailMessages.OUTPUT_FALLBACK;
            }
        }

        conversations.append(conversationId, MessageRole.ASSISTANT, text);
        recordUsage(conversationId, reply, command.attachedImage().isPresent());

        log.info("Session {} answered on conversation {}{}{}",
                command.sessionKey(), conversationId,
                reply.quotationNumber() == null ? "" : " with quotation " + reply.quotationNumber(),
                reply.complaintNumber() == null ? "" : " with complaint " + reply.complaintNumber()
                        + (reply.orderNumber() == null ? "" : " with order " + reply.orderNumber())
                );

        return new ChatTurn(conversationId, 
                            text,
                            reply.quotationNumber(), 
                            reply.complaintNumber(),
                            reply.orderNumber(), 
                            reply.vision());
    }

    /**
     * A blocked message is stored as a placeholder, never verbatim: history is
     * replayed to the model on later turns, and an injection kept in it would
     * be replayed with it.
     */
    private ChatTurn refuse(ChatCommand command, UUID conversationId, GuardVerdict verdict) {

        log.warn("Session {} message blocked, rule {}", command.sessionKey(), verdict.rule());
        recordEvent(conversationId, Stage.INPUT, verdict.rule());

        conversations.append(conversationId, MessageRole.USER, BLOCKED_MARKER);
        conversations.append(conversationId, MessageRole.ASSISTANT, GuardrailMessages.INPUT_REFUSAL);

        return new ChatTurn(conversationId, GuardrailMessages.INPUT_REFUSAL, null, null, null, null);
    }

    private static List<String> texts(List<Message> history, String current) {

        List<String> all = new ArrayList<>();
        history.forEach(message -> all.add(message.content()));
        all.add(current);
        return all;
    }

    /** An audit write must never cost a customer their answer. */
    private void recordEvent(UUID conversationId, Stage stage, String rule) {
        try {
            guardrailEvents.record(conversationId, stage, rule);

        } catch (RuntimeException failure) {
            log.warn("Could not record guardrail event {} for conversation {}", 
            rule, conversationId, failure);
        }
    }

    /** A metrics write must never cost a customer their answer. */
    private void recordUsage(UUID conversationId, AgentReply reply, boolean hadImage) {
        reply.measuredUsage().ifPresent(usage -> {
            try {
                usageRepository.record(conversationId, usage, hadImage);
                
            } catch (RuntimeException failure) {
                log.warn("Could not record token usage for conversation {}", 
                conversationId, failure);
            }
        });
    }

    /**
     * The stored form of a turn that carried a photo. The marker is what the
     * panel renders as a thumbnail placeholder on a rebuilt history, and what
     * tells the model, on a later turn, that a photo was discussed earlier.
     */
    private static String remembered(ChatCommand command) {
        return command.attachedImage()
                .map(image -> PHOTO_MARKER + " " + command.message())
                .orElseGet(command::message);
    }

    /** What is stored in place of a message the input guard blocked. */
    public static final String BLOCKED_MARKER = "[mensaje bloqueado]";

    /** Prefix marking a turn that arrived with a photo. */
    public static final String PHOTO_MARKER = "[foto]";
}
