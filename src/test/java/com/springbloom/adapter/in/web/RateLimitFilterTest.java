package com.springbloom.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.springbloom.config.RateLimitProperties;
import com.springbloom.config.RateLimitProperties.Rule;
import com.springbloom.domain.model.GuardrailEvent.Stage;
import com.springbloom.domain.port.out.GuardrailEventRepository;
import com.springbloom.domain.service.guardrail.GuardrailMessages;

class RateLimitFilterTest {

    private GuardrailEventRepository events;
    private RateLimitProperties properties;
    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        events = mock(GuardrailEventRepository.class);
        properties = new RateLimitProperties();
        properties.setMessages(new Rule(2, 1));
        filter = new RateLimitFilter(properties, events,
                Clock.fixed(Instant.parse("2026-11-23T12:00:00Z"), ZoneOffset.UTC));
    }

    private MockHttpServletResponse send(String method, String path, String ip) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr("10.0.0.5");
        if (ip != null) {
            request.addHeader("CF-Connecting-IP", ip);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    @DisplayName("the third message in a burst of two is a 429 with Retry-After and a Spanish body")
    void rejectsPastTheBurst() throws Exception {
        assertThat(send("POST", "/api/chat/messages", "203.0.113.7").getStatus()).isEqualTo(200);
        assertThat(send("POST", "/api/chat/messages", "203.0.113.7").getStatus()).isEqualTo(200);

        MockHttpServletResponse denied = send("POST", "/api/chat/messages", "203.0.113.7");

        assertThat(denied.getStatus()).isEqualTo(429);
        assertThat(Integer.parseInt(denied.getHeader("Retry-After"))).isPositive();
        assertThat(denied.getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .contains(GuardrailMessages.RATE_LIMITED);
    }

    @Test
    @DisplayName("another IP has its own bucket, and rotating session keys does not help")
    void limitsPerIp() throws Exception {
        send("POST", "/api/chat/messages", "203.0.113.7");
        send("POST", "/api/chat/messages", "203.0.113.7");

        assertThat(send("POST", "/api/chat/messages", "203.0.113.7").getStatus()).isEqualTo(429);
        assertThat(send("POST", "/api/chat/messages", "198.51.100.9").getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("a junk header falls back to the socket address")
    void junkHeaderUsesSocket() throws Exception {
        send("POST", "/api/chat/messages", "not-an-ip");
        send("POST", "/api/chat/messages", "also junk");

        assertThat(send("POST", "/api/chat/messages", "<x>").getStatus()).isEqualTo(429);
    }

    @Test
    @DisplayName("only /api/chat is limited: webhook, cart and pages never are")
    void leavesOtherPathsAlone() throws Exception {
        for (int i = 0; i < 20; i++) {
            assertThat(send("POST", "/api/payments/stripe/webhook", "203.0.113.7").getStatus()).isEqualTo(200);
            assertThat(send("GET", "/api/cart", "203.0.113.7").getStatus()).isEqualTo(200);
            assertThat(send("GET", "/", "203.0.113.7").getStatus()).isEqualTo(200);
        }
    }

    @Test
    @DisplayName("history reads have their own, larger bucket")
    void historyIsSeparate() throws Exception {
        send("POST", "/api/chat/messages", "203.0.113.7");
        send("POST", "/api/chat/messages", "203.0.113.7");

        assertThat(send("GET", "/api/chat/history", "203.0.113.7").getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("a flooding IP writes one audit row a minute, not one per rejection")
    void auditIsThrottled() throws Exception {
        for (int i = 0; i < 10; i++) {
            send("POST", "/api/chat/messages", "203.0.113.7");
        }

        verify(events, times(1)).record(eq(null), eq(Stage.INPUT), eq("rate_limited"));
    }

    @Test
    @DisplayName("a failing audit write never turns a 429 into a 500")
    void auditFailureIsHarmless() throws Exception {
        doThrow(new IllegalStateException("db down")).when(events).record(any(), any(), any());
        send("POST", "/api/chat/messages", "203.0.113.7");
        send("POST", "/api/chat/messages", "203.0.113.7");

        assertThat(send("POST", "/api/chat/messages", "203.0.113.7").getStatus()).isEqualTo(429);
    }

    @Test
    @DisplayName("it can be switched off")
    void canBeDisabled() throws Exception {
        properties.setEnabled(false);

        for (int i = 0; i < 10; i++) {
            assertThat(send("POST", "/api/chat/messages", "203.0.113.7").getStatus()).isEqualTo(200);
        }
        verify(events, never()).record(any(), any(), any());
    }
}
