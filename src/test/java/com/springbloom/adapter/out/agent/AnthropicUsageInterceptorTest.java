package com.springbloom.adapter.out.agent;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

import com.springbloom.domain.model.TokenUsage;

import static org.assertj.core.api.Assertions.assertThat;

class AnthropicUsageInterceptorTest {

    private static final String BODY = """
            {"id":"msg_1","model":"claude-haiku-4-5-20251001","content":[],
             "usage":{"input_tokens":42,"cache_creation_input_tokens":4500,
                      "cache_read_input_tokens":0,"output_tokens":120}}""";

    private final AnthropicUsageInterceptor interceptor = new AnthropicUsageInterceptor();

    @AfterEach
    void clear() {
        TurnUsageCollector.end();
    }

    private ClientHttpResponse call(String url, HttpStatus status) throws Exception {
        return interceptor.intercept(
                new MockClientHttpRequest(HttpMethod.POST, URI.create(url)), new byte[0],
                (request, body) -> new MockClientHttpResponse(BODY.getBytes(StandardCharsets.UTF_8), status));
    }

    @Test
    @DisplayName("usage is read from an Anthropic messages response, and the body is still readable after")
    void readsAndReplays() throws Exception {
        TurnUsageCollector.begin();

        ClientHttpResponse response = call("https://api.anthropic.com/v1/messages", HttpStatus.OK);

        assertThat(new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8)).contains("msg_1");
        assertThat(TurnUsageCollector.end().orElseThrow())
                .isEqualTo(new TokenUsage("claude-haiku-4-5-20251001", 42, 120, 0, 4500));
    }

    @Test
    @DisplayName("other hosts, other paths and error responses are ignored")
    void ignoresTheRest() throws Exception {
        TurnUsageCollector.begin();

        call("https://api.openai.com/v1/embeddings", HttpStatus.OK);
        call("https://api.anthropic.com/v1/models", HttpStatus.OK);
        call("https://api.anthropic.com/v1/messages", HttpStatus.TOO_MANY_REQUESTS);

        assertThat(TurnUsageCollector.end()).isEmpty();
    }

    @Test
    @DisplayName("an unreadable body is swallowed, not thrown at the customer")
    void malformedBody() throws Exception {
        TurnUsageCollector.begin();

        ClientHttpResponse response = interceptor.intercept(
                new MockClientHttpRequest(HttpMethod.POST, URI.create("https://api.anthropic.com/v1/messages")),
                new byte[0],
                (request, body) -> new MockClientHttpResponse("not json".getBytes(StandardCharsets.UTF_8), HttpStatus.OK));

        assertThat(response).isNotNull();
        assertThat(TurnUsageCollector.end()).isEmpty();
    }
}
