package com.springbloom.adapter.in.chat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.springbloom.config.SecurityConfig;
import com.springbloom.domain.port.in.ChatUseCase;
import com.springbloom.domain.port.in.ClassifyFlowerUseCase;
import com.springbloom.domain.port.in.RequestQuotationUseCase;
import com.springbloom.domain.service.AgentUnavailableException;
import com.springbloom.domain.service.guardrail.GuardrailMessages;

/** The wire contract of POST /api/chat/messages. The use case is mocked. */
@WebMvcTest(ChatController.class)
@Import(SecurityConfig.class)
class ChatControllerMessagesTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatUseCase chat;
    @MockitoBean
    private RequestQuotationUseCase requestQuotation;
    @MockitoBean
    private ClassifyFlowerUseCase classifyFlower;

    @Test
    @DisplayName("a model failure is a clean 503 with a Spanish message and no internals")
    void modelFailureIs503() throws Exception {
        when(chat.say(any())).thenThrow(new AgentUnavailableException(new RuntimeException("anthropic 529 overloaded")));

        mockMvc.perform(post("/api/chat/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionKey\":\"web-abcdef123456\",\"message\":\"hola\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(GuardrailMessages.MODEL_UNAVAILABLE))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("anthropic"))));
    }

    @Test
    @DisplayName("a malformed session key is a 400 and never reaches the use case")
    void badSessionKeyIs400() throws Exception {
        mockMvc.perform(post("/api/chat/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionKey\":\"x'; DROP TABLE--\",\"message\":\"hola\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(chat);
    }

    @Test
    @DisplayName("an oversized image payload is refused before it is decoded")
    void hugeImageIs400() throws Exception {
        String huge = "A".repeat(14_000_001);

        mockMvc.perform(post("/api/chat/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionKey\":\"web-abcdef123456\",\"message\":\"hola\",\"image\":\"" + huge + "\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(chat);
    }
}
