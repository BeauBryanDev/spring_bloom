package com.springbloom.domain.port.in;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.springbloom.domain.model.Message;
import com.springbloom.domain.model.VisionEvidence;
import com.springbloom.domain.model.vo.ImageAttachment;

/**
 * One turn of the public chat: the customer says something, Florabelle answers,
 * and both halves are remembered.
 */
public interface ChatUseCase {

    ChatTurn say(ChatCommand command);

    /**
     * The session's past turns, oldest first, so a browser that navigated away
     * can rebuild the panel instead of starting over. Reading history never
     * starts a conversation: an unknown session key is an empty history.
     */
    ChatHistory history(String sessionKey, int limit);

    /**
     * sessionKey identifies an anonymous browser; there is no login.
     *
     * image is the photo the customer dropped on the panel, if any. A turn may
     * carry a photo with no words - dropping a flower on Florabelle is itself
     * the question - so the message is only required when there is no image.
     */
    record ChatCommand(String sessionKey, String message, ImageAttachment image) {

        public ChatCommand {

            if (sessionKey == null || sessionKey.isBlank()) {
                throw new IllegalArgumentException("A session key is required");
            }

            if (message == null || message.isBlank()) {
                if (image == null) {
                    throw new IllegalArgumentException("A message is required");
                }
                message = PHOTO_ONLY_MESSAGE;
            }
            
            if (message.length() > MAX_MESSAGE_LENGTH) {
                throw new IllegalArgumentException(
                        "A message cannot exceed " + MAX_MESSAGE_LENGTH + " characters");
            }
        }

        public ChatCommand(String sessionKey, String message) {
            this(sessionKey, message, null);
        }

        public Optional<ImageAttachment> attachedImage() {
            return Optional.ofNullable(image);
        }

        /** A chat turn is not a document upload; the column is TEXT but the agent is not free. */
        public static final int MAX_MESSAGE_LENGTH = 2000;

        /**
         * What a photo with no words is remembered as. It is Spanish because it
         * is stored in the same column the customer's own words go into, and the
         * panel replays it verbatim.
         */
        public static final String PHOTO_ONLY_MESSAGE = "Le comparto esta foto.";
    }

    /**
     * conversationId is null when this session has never said anything.
     * Messages are customer-visible only: prompt scaffolding is not history.
     */
    record ChatHistory(UUID conversationId, List<Message> messages) {

        public static final int MAX_TURNS = 100;

        public ChatHistory {
            messages = messages == null ? List.of() : List.copyOf(messages);
        }

        public boolean empty() {
            return messages.isEmpty();
        }
    }

    /**
     * @param quotationNumber present when this turn produced a quotation
     * @param complaintNumber present when this turn filed a claim
     * @param vision present when this turn ran a photo through the trained model
     */
    record ChatTurn(
            UUID conversationId,
            String reply,
            String quotationNumber,
            String complaintNumber,
            VisionEvidence vision) {

        public Optional<String> quotation() {
            return Optional.ofNullable(quotationNumber);
        }

        public Optional<String> complaint() {
            return Optional.ofNullable(complaintNumber);
        }
    }
}
