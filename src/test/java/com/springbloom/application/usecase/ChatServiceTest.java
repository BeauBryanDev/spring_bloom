package com.springbloom.application.usecase;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import com.springbloom.domain.model.Conversation;
import com.springbloom.domain.model.Message;
import com.springbloom.domain.model.MessageRole;
import com.springbloom.domain.port.in.ChatUseCase.ChatCommand;
import com.springbloom.domain.port.in.ChatUseCase.ChatHistory;
import com.springbloom.domain.port.in.ChatUseCase.ChatTurn;
import com.springbloom.domain.port.out.ChatAgentPort;
import com.springbloom.domain.port.out.ChatAgentPort.AgentReply;
import com.springbloom.domain.port.out.ChatAgentPort.AgentRequest;
import com.springbloom.domain.port.out.ConversationRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Remember, ask, remember. The model is a mocked port. */
class ChatServiceTest {

    private static final String SESSION = "session-abc";
    private static final UUID CONVERSATION_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-08-26T15:00:00Z");

    private ConversationRepository conversations;
    private ChatAgentPort agent;
    private ChatService service;

    @BeforeEach
    void setUp() {
        conversations = mock(ConversationRepository.class);
        agent = mock(ChatAgentPort.class);
        service = new ChatService(conversations, agent);

        when(conversations.findOrStart(SESSION)).thenReturn(
                new Conversation(CONVERSATION_ID, null, SESSION, List.of(), NOW));
        when(conversations.history(eq(CONVERSATION_ID), anyInt())).thenReturn(List.of());
        when(agent.reply(any())).thenReturn(AgentReply.of("Con gusto, tenemos rosas."));
    }

    @Test
    @DisplayName("both halves of the turn are remembered, in order")
    void persistsBothHalvesOfTheTurn() {
        ChatTurn turn = service.say(new ChatCommand(SESSION, "Hola, quiero rosas"));

        assertThat(turn.conversationId()).isEqualTo(CONVERSATION_ID);
        assertThat(turn.reply()).isEqualTo("Con gusto, tenemos rosas.");

        InOrder order = inOrder(conversations, agent);
        order.verify(conversations).findOrStart(SESSION);
        order.verify(conversations).history(eq(CONVERSATION_ID), anyInt());
        order.verify(conversations).append(CONVERSATION_ID, MessageRole.USER, "Hola, quiero rosas");
        order.verify(agent).reply(any());
        order.verify(conversations).append(
                CONVERSATION_ID, MessageRole.ASSISTANT, "Con gusto, tenemos rosas.");
    }

    @Test
    @DisplayName("history is read before the new turn is appended, so it is not replayed twice")
    void doesNotReplayTheMessageBeingAnswered() {
        service.say(new ChatCommand(SESSION, "Hola"));

        InOrder order = inOrder(conversations);
        order.verify(conversations).history(eq(CONVERSATION_ID), anyInt());
        order.verify(conversations).append(CONVERSATION_ID, MessageRole.USER, "Hola");
    }

    @Test
    @DisplayName("past turns reach the agent, and the new message travels separately")
    void handsTheAgentItsHistory() {
        List<Message> past = List.of(
                new Message(1L, CONVERSATION_ID, MessageRole.USER, "Buenas", NOW),
                new Message(2L, CONVERSATION_ID, MessageRole.ASSISTANT, "Hola, soy Florabelle", NOW));
        when(conversations.history(eq(CONVERSATION_ID), anyInt())).thenReturn(past);

        service.say(new ChatCommand(SESSION, "Quiero rosas"));

        ArgumentCaptor<AgentRequest> captor = ArgumentCaptor.forClass(AgentRequest.class);
        verify(agent).reply(captor.capture());

        assertThat(captor.getValue().sessionKey()).isEqualTo(SESSION);
        assertThat(captor.getValue().customerMessage()).isEqualTo("Quiero rosas");
        assertThat(captor.getValue().history()).hasSize(2);
        assertThat(captor.getValue().history())
                .extracting(Message::content)
                .doesNotContain("Quiero rosas");
    }

    @Test
    @DisplayName("a quotation raised during the turn is surfaced without parsing the prose")
    void surfacesAQuotationNumber() {
        when(agent.reply(any())).thenReturn(
                new AgentReply("Le preparé la cotización COT-20260826-48395.", "COT-20260826-48395", null));

        ChatTurn turn = service.say(new ChatCommand(SESSION, "Cotíceme 12 rosas"));

        assertThat(turn.quotationNumber()).isEqualTo("COT-20260826-48395");
        assertThat(turn.quotation()).contains("COT-20260826-48395");
    }

    @Test
    @DisplayName("an ordinary turn carries no quotation")
    void leavesTheQuotationEmptyOtherwise() {
        assertThat(service.say(new ChatCommand(SESSION, "Hola")).quotation()).isEmpty();
    }

    @Test
    @DisplayName("what the customer said is remembered even when the model then fails")
    void keepsTheCustomerTurnWhenTheModelFails() {
        when(agent.reply(any())).thenThrow(new IllegalStateException("model unavailable"));

        assertThatThrownBy(() -> service.say(new ChatCommand(SESSION, "Hola")))
                .isInstanceOf(IllegalStateException.class);

        verify(conversations).append(CONVERSATION_ID, MessageRole.USER, "Hola");
        verify(conversations, never()).append(
                eq(CONVERSATION_ID), eq(MessageRole.ASSISTANT), any());
    }

    @Test
    @DisplayName("history replays the customer-visible turns, oldest first")
    void replaysTheStoredTurns() {
        when(conversations.findBySessionKey(SESSION)).thenReturn(java.util.Optional.of(
                new Conversation(CONVERSATION_ID, null, SESSION, List.of(), NOW)));
        when(conversations.history(eq(CONVERSATION_ID), anyInt())).thenReturn(List.of(
                new Message(1L, CONVERSATION_ID, MessageRole.SYSTEM, "scaffolding", NOW),
                new Message(2L, CONVERSATION_ID, MessageRole.USER, "Hola", NOW),
                new Message(3L, CONVERSATION_ID, MessageRole.ASSISTANT, "Con gusto", NOW)));

        ChatHistory history = service.history(SESSION, 40);

        assertThat(history.conversationId()).isEqualTo(CONVERSATION_ID);
        assertThat(history.messages()).extracting(Message::content)
                .containsExactly("Hola", "Con gusto");
    }

    @Test
    @DisplayName("reading history never starts a conversation")
    void doesNotStartAConversationJustToRead() {
        when(conversations.findBySessionKey("unseen")).thenReturn(java.util.Optional.empty());

        ChatHistory history = service.history("unseen", 40);

        assertThat(history.empty()).isTrue();
        assertThat(history.conversationId()).isNull();
        verify(conversations, never()).findOrStart(any());
    }

    @Test
    @DisplayName("the command refuses what should never reach the model")
    void guardsItsCommand() {
        assertThatThrownBy(() -> new ChatCommand(SESSION, " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChatCommand(" ", "Hola"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChatCommand(SESSION, "x".repeat(2001)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
