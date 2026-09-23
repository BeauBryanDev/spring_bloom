package com.springbloom.adapter.out.persistence.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.springbloom.domain.model.Conversation;
import com.springbloom.domain.model.Message;
import com.springbloom.domain.model.MessageRole;
import com.springbloom.domain.port.out.ConversationRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Writes real conversations and turns. message_role is the third Postgres enum
 * in this schema, and a read-only suite would never catch a bad binding.
 *
 *   set -a; . ./.env; set +a; ./mvnw test
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_USER", matches = ".+")
class ConversationRepositoryAdapterTest {

    @Autowired
    private ConversationRepository conversations;

    @Autowired
    private JdbcTemplate jdbc;

    private final List<UUID> written = new ArrayList<>();

    @AfterEach
    void removeWhatThisTestWrote() {
        written.forEach(id -> jdbc.update("DELETE FROM conversation WHERE id = ?", id));
        written.clear();
    }

    private Conversation start(String suffix) {
        Conversation conversation = conversations.findOrStart("test-" + suffix + "-" + UUID.randomUUID());
        written.add(conversation.id());
        return conversation;
    }

    @Test
    @DisplayName("a session key that has not been seen starts a conversation")
    void startsAConversationForANewSessionKey() {
        Conversation started = start("new");

        assertThat(started.persisted()).isTrue();
        assertThat(started.id()).isNotNull();
        assertThat(started.customerId()).as("anonymous until a quotation needs a customer").isNull();
        assertThat(started.createdAt()).isNotNull();
        assertThat(started.messages()).isEmpty();

        assertThat(conversations.findById(started.id())).isPresent();
        assertThat(conversations.findBySessionKey(started.sessionKey()))
                .get()
                .extracting(Conversation::id)
                .isEqualTo(started.id());
    }

    @Test
    @DisplayName("the same session key returns the same conversation, never a second one")
    void reusesTheConversationForAKnownSessionKey() {
        Conversation first = start("repeat");
        Conversation again = conversations.findOrStart(first.sessionKey());

        assertThat(again.id()).isEqualTo(first.id());
        assertThat(rowsForSessionKey(first.sessionKey())).isEqualTo(1);
    }

    @Test
    @DisplayName("every message_role binds on the write, not just on the read")
    void appendsEveryRole() {
        Conversation conversation = start("roles");

        Message system = conversations.append(
                conversation.id(), MessageRole.SYSTEM, "Eres Florabelle.");
        Message user = conversations.append(
                conversation.id(), MessageRole.USER, "Hola, quiero rosas");
        Message assistant = conversations.append(
                conversation.id(), MessageRole.ASSISTANT, "Con gusto, tenemos rosas");

        assertThat(system.persisted()).isTrue();
        assertThat(user.conversationId()).isEqualTo(conversation.id());
        assertThat(assistant.createdAt()).isNotNull();

        assertThat(jdbc.queryForList(
                "SELECT role::text FROM message WHERE conversation_id = ? ORDER BY id",
                String.class, conversation.id()))
                .containsExactly("SYSTEM", "USER", "ASSISTANT");
    }

    @Test
    @DisplayName("content is stored verbatim, accents and newlines included")
    void storesContentVerbatim() {
        Conversation conversation = start("verbatim");
        String content = "¿Tienen peonías?\nQuiero 12 tallos — para el sábado.";

        conversations.append(conversation.id(), MessageRole.USER, content);

        assertThat(conversations.history(conversation.id(), 10))
                .singleElement()
                .extracting(Message::content)
                .isEqualTo(content);
    }

    @Test
    @DisplayName("history reads the newest turns but hands them back oldest first")
    void returnsABoundedTailInReadingOrder() {
        Conversation conversation = start("history");
        for (int turn = 1; turn <= 12; turn++) {
            conversations.append(conversation.id(), MessageRole.USER, "turno " + turn);
        }

        List<Message> all = conversations.history(conversation.id(), 50);
        assertThat(all).hasSize(12);
        assertThat(all.get(0).content()).isEqualTo("turno 1");
        assertThat(all.get(11).content()).isEqualTo("turno 12");

        List<Message> tail = conversations.history(conversation.id(), 4);
        assertThat(tail).extracting(Message::content)
                .as("the last four, still in reading order")
                .containsExactly("turno 9", "turno 10", "turno 11", "turno 12");
    }

    /**
     * The guard throws IllegalArgumentException, but @Repository puts the bean
     * behind PersistenceExceptionTranslationInterceptor, which rewrites it as
     * InvalidDataAccessApiUsageException on the way out. Asserting the type a
     * caller actually sees, not the one the adapter threw.
     */
    @Test
    @DisplayName("a non-positive history limit is a programming error, not an empty list")
    void refusesANonPositiveLimit() {
        Conversation conversation = start("limit");

        assertThatThrownBy(() -> conversations.history(conversation.id(), 0))
                .isInstanceOf(InvalidDataAccessApiUsageException.class)
                .hasMessageContaining("limit must be positive");
    }

    @Test
    @DisplayName("SYSTEM turns are filtered out of what the customer is shown")
    void separatesPromptScaffoldingFromTheChat() {
        Conversation conversation = start("visible");
        conversations.append(conversation.id(), MessageRole.SYSTEM, "Eres Florabelle.");
        conversations.append(conversation.id(), MessageRole.USER, "Hola");

        Conversation loaded = conversation.withMessages(conversations.history(conversation.id(), 10));

        assertThat(loaded.messages()).hasSize(2);
        assertThat(loaded.customerVisibleMessages())
                .extracting(Message::role)
                .containsExactly(MessageRole.USER);
        assertThat(loaded.lastMessage()).get().extracting(Message::content).isEqualTo("Hola");
    }

    @Test
    @DisplayName("a customer can be attached once the chat has produced one")
    void attachesACustomer() {
        Conversation conversation = start("customer");
        Long customerId = jdbc.queryForObject("SELECT id FROM customer LIMIT 1", Long.class);

        Conversation assigned = conversations.assignCustomer(conversation.id(), customerId);

        assertThat(assigned.customerId()).isEqualTo(customerId);
        assertThat(assigned.customer()).contains(customerId);
        assertThat(conversations.findById(conversation.id()))
                .get().extracting(Conversation::customerId).isEqualTo(customerId);
    }

    @Test
    @DisplayName("concurrent first turns join one conversation instead of colliding")
    void resolvesARaceOnTheSameSessionKey() throws Exception {
        String sessionKey = "test-race-" + UUID.randomUUID();
        int racers = 6;

        try (ExecutorService pool = Executors.newFixedThreadPool(racers)) {
            List<Callable<Conversation>> starts = new ArrayList<>();
            for (int i = 0; i < racers; i++) {
                starts.add(() -> conversations.findOrStart(sessionKey));
            }

            List<UUID> ids = new ArrayList<>();
            for (Future<Conversation> future : pool.invokeAll(starts)) {
                ids.add(future.get().id());
            }
            written.addAll(ids);

            assertThat(ids).as("every racer got the same conversation").containsOnly(ids.get(0));
        }

        assertThat(rowsForSessionKey(sessionKey)).isEqualTo(1);
    }

    private Integer rowsForSessionKey(String sessionKey) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM conversation WHERE session_key = ?", Integer.class, sessionKey);
    }
}
