package com.springbloom.domain.port.out;

import java.util.List;
import java.util.Optional;

import com.springbloom.domain.model.Message;
import com.springbloom.domain.model.VisionEvidence;
import com.springbloom.domain.model.vo.ImageAttachment;

/**
 * The conversational agent, as the domain sees it: history and a customer's
 * words in, a reply out. Which model answers, and how it reaches the use cases,
 * is entirely the adapter's business.
 */
public interface ChatAgentPort {

    AgentReply reply(AgentRequest request);

    /**
     * The session key is passed so the adapter can bind its tools to this
     * conversation. It is deliberately not something the model can see or
     * choose: a model that could name a session could quote into someone
     * else's.
     */
    record AgentRequest(
            String sessionKey,
            List<Message> history,
            String customerMessage,
            ImageAttachment image) {

        public AgentRequest {
            if (sessionKey == null || sessionKey.isBlank()) {
                throw new IllegalArgumentException("A session key is required");
            }
            if (customerMessage == null || customerMessage.isBlank()) {
                throw new IllegalArgumentException("A message is required");
            }
            history = history == null ? List.of() : List.copyOf(history);
        }

        public AgentRequest(String sessionKey, List<Message> history, String customerMessage) {
            this(sessionKey, history, customerMessage, null);
        }

        /** The photo attached to this turn, if the customer sent one. */
        public Optional<ImageAttachment> attachedImage() {
            return Optional.ofNullable(image);
        }
    }

    /**
     The Domain knows nothing about Anthropic, SpringAI or the model's
     vocabulary, so the adapter must translate the model's words into the
     domain's terms. The agent's reply is the only thing the domain knows about
     the model's words, so the adapter must translate the agent's words into the
     domain's terms.  HEAGONAL ARCHITECTURE RULES!
     knows about the model's words, so the adapter must translate the agent's
     words into the domain's terms.
     */
    record AgentReply(
            String text, String quotationNumber, String complaintNumber, VisionEvidence vision) {

        public AgentReply {
            if (text == null || text.isBlank()) {
                throw new IllegalArgumentException("The agent must say something");
            }
        }

        public AgentReply(String text, String quotationNumber, String complaintNumber) {
            this(text, quotationNumber, complaintNumber, null);
        }

        public static AgentReply of(String text) {
            return new AgentReply(text, null, null, null);
        }
    }
}
