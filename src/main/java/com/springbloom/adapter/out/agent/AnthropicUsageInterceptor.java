package com.springbloom.adapter.out.agent;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Reads the usage block of every non-streaming Anthropic messages response and
 * hands it to the turn's collector. The body is buffered and replayed, so
 * Spring AI still reads it as if nothing had happened. Anything that goes
 * wrong here is logged and swallowed: metrics must never break an answer.
 */
public class AnthropicUsageInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AnthropicUsageInterceptor.class);

    private static final ObjectMapper JSON = new ObjectMapper();

    @Override
    public ClientHttpResponse intercept(HttpRequest request, 
                                        byte[] body,
                                        ClientHttpRequestExecution execution)
                               throws IOException {

        ClientHttpResponse response = execution.execute(request, body);
        if (!isMessagesCall(request) || !response.getStatusCode().is2xxSuccessful()) {
            return response;
        }
        try {
            byte[] payload = response.getBody().readAllBytes();
            record(payload);
            return new Replayed(response, payload);
            
        } catch (IOException | RuntimeException failure) {
            log.warn("Could not read token usage from the Anthropic response", failure);
            return response;
        }
    }

    static boolean isMessagesCall(HttpRequest request) {
        return request.getURI().getHost() != null
                && request.getURI().getHost().endsWith("anthropic.com")
                && request.getURI().getPath().endsWith("/v1/messages");
    }

    static void record(byte[] payload) throws IOException {
        JsonNode root = JSON.readTree(payload);
        JsonNode usage = root.path("usage");
        if (usage.isMissingNode()) {
            return;
        }
        TurnUsageCollector.add(
                root.path("model").asText(null),
                usage.path("input_tokens").asInt(0),
                usage.path("output_tokens").asInt(0),
                usage.path("cache_read_input_tokens").asInt(0),
                usage.path("cache_creation_input_tokens").asInt(0));
    }

    private static final class Replayed implements ClientHttpResponse {

        private final ClientHttpResponse original;
        private final byte[] payload;

        Replayed(ClientHttpResponse original, byte[] payload) {
            this.original = original;
            this.payload = payload;
        }

        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(payload);
        }

        @Override
        public HttpHeaders getHeaders() {
            return original.getHeaders();
        }

        @Override
        public HttpStatusCode getStatusCode() throws IOException {
            return original.getStatusCode();
        }

        @Override
        public String getStatusText() throws IOException {
            return original.getStatusText();
        }

        @Override
        public void close() {
            original.close();
        }
    }
}
