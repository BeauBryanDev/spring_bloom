package com.springbloom.application.usecase;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.springbloom.domain.model.Conversation;
import com.springbloom.domain.model.Message;
import com.springbloom.domain.model.MessageRole;
import com.springbloom.domain.port.in.ChatUseCase;
import com.springbloom.domain.port.out.ChatAgentPort;
import com.springbloom.domain.port.out.ChatAgentPort.AgentReply;
import com.springbloom.domain.port.out.ChatAgentPort.AgentRequest;
import com.springbloom.domain.port.out.ConversationRepository;

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

        conversations.append(conversationId, MessageRole.USER, remembered(command));

        AgentReply reply = agent.reply(new AgentRequest(
                command.sessionKey(), history, command.message(), command.image()
                                                        ));

        conversations.append(conversationId, MessageRole.ASSISTANT, reply.text());

        log.info("Session {} answered on conversation {}{}{}",
                command.sessionKey(), conversationId,
                reply.quotationNumber() == null ? "" : " with quotation " + reply.quotationNumber(),
                reply.complaintNumber() == null ? "" : " with complaint " + reply.complaintNumber()
                );

        return new ChatTurn(conversationId, reply.text(),
                reply.quotationNumber(), reply.complaintNumber(), reply.vision());
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

    /** Prefix marking a turn that arrived with a photo. */
    public static final String PHOTO_MARKER = "[foto]";
}
