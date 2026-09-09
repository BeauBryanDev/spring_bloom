package com.springbloom.domain.port.in;

import java.util.Optional;

import com.springbloom.domain.model.Complaint;
import com.springbloom.domain.model.ComplaintType;

/**
 * Records a customer's claim. Filing is all this does: whether a claim is
 * granted is a decision the shop makes later, never the chat.
 */
public interface FileComplaintUseCase {

    Complaint file(FileComplaintCommand command);

    /**
     * @param orderNumber the ORD-... number if the customer has it, else null;
     *   an unrecognized number does not lose the claim
     */
    record FileComplaintCommand(
            String sessionKey, ComplaintType type, String description, String orderNumber) {

        public FileComplaintCommand {

            if (sessionKey == null || sessionKey.isBlank()) {
                throw new IllegalArgumentException("A session key is required");
            }

            if (type == null) {
                throw new IllegalArgumentException("A complaint type is required");
            }

            if (type == ComplaintType.OTHER) {
                throw new IllegalArgumentException("Other complaints are not allowed");
            }

            if (description == null || description.isBlank()) {
                throw new IllegalArgumentException("A complaint needs a description");
            }

            orderNumber = orderNumber == null || orderNumber.isBlank() ? null : orderNumber.trim();
        }
        

        public Optional<String> order() {

            return Optional.ofNullable(orderNumber);
        }
    }
}
