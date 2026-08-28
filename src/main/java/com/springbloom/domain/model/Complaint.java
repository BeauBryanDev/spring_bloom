package com.springbloom.domain.model;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * A customer's claim about an order: damaged flowers, a late delivery, the
 * wrong item. Recorded, never judged, by the agent: closing a claim is a
 * decision the shop makes, not the chat.
 *
 * orderId is null while we do not yet know which purchase the customer means.
 * The chat is anonymous and a claim is worth recording even from someone who
 * cannot find their order number.
 */
public record Complaint(
        Long id,
        String complaintNumber,
        Long orderId,
        Long customerId,
        UUID conversationId,
        ComplaintType type,
        ComplaintStatus status,
        String description,
        String resolution,
        Instant createdAt,
        Instant resolvedAt) {

    public Complaint {

        if (type == null) {
            throw new IllegalArgumentException("type is required");
        }
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("A complaint needs a description");
        }
        if (description.length() > MAX_DESCRIPTION) {
            throw new IllegalArgumentException(
                    "A complaint description cannot exceed " + MAX_DESCRIPTION + " characters");
        }
        // Mirrors ck_complaint_closed_has_date and ck_complaint_closed_has_resolution:
        // an invalid claim cannot exist in memory, let alone reach an INSERT.
        if (status.closed()) {

            if (resolution == null || resolution.isBlank()) {

                throw new IllegalArgumentException(status + " requires a resolution");
            }
            if (resolvedAt == null) {

                throw new IllegalArgumentException(status + " requires a resolution date");
            }
        } else if (resolvedAt != null) {

            throw new IllegalArgumentException("An open complaint cannot have a resolution date");
        }
    }

    public static final int MAX_DESCRIPTION = 2000;

    /** A claim as the agent files it: open, unresolved, not yet numbered. */
    public static Complaint filed(ComplaintType type, String description) {

        return new Complaint(null, null, null, null, null,
                type, ComplaintStatus.OPEN, description, null, null, null);
    }

    public boolean persisted() {
        return id != null;
    }

    public boolean open() {
        return !status.closed();
    }

    /** Absent when the customer could not tell us which order they mean. */
    public Optional<Long> order() {

        return Optional.ofNullable(orderId);
    }

    public Optional<String> resolutionText() {

        return Optional.ofNullable(resolution);
    }

    /** Replaced whole on each insert attempt, like a quotation number. */
    public Complaint withNumber(String number) {

        return new Complaint(id, number, orderId, customerId, conversationId,
                type, status, description, resolution, createdAt, resolvedAt);
    }

    public Complaint withId(Long assignedId) {

        return new Complaint(assignedId, complaintNumber, orderId, customerId, conversationId,
                type, status, description, resolution, createdAt, resolvedAt);
    }

    public Complaint withOrder(Long order) {

        return new Complaint(id, complaintNumber, order, customerId, conversationId,
                type, status, description, resolution, createdAt, resolvedAt);
    }

    public Complaint withConversation(UUID conversation) {

        return new Complaint(id, complaintNumber, orderId, customerId, conversation,
                type, status, description, resolution, createdAt, resolvedAt);
    }

    public Complaint withCustomer(Long customer) {

        return new Complaint(id, complaintNumber, orderId, customer, conversationId,
                type, status, description, resolution, createdAt, resolvedAt);
    }

    /** Moving to IN_REVIEW keeps the claim open; the shop is looking at it. */
    public Complaint takenIntoReview() {

        return new Complaint(id, complaintNumber, orderId, customerId, conversationId,
                type, ComplaintStatus.IN_REVIEW, description, resolution, createdAt, null);
    }

    /** Closing always states what was decided and when, as the schema demands. */
    public Complaint closedAs(ComplaintStatus outcome,
         String howItWasResolved, 
         Instant when) {

        if (outcome == null || !outcome.closed()) {

            throw new IllegalArgumentException("Only RESOLVED or REJECTED close a complaint");
        }
        return new Complaint(id, 
            complaintNumber, 
            orderId, 
            customerId, 
            conversationId,
            type, 
            outcome, 
            description, 
            howItWasResolved, 
            createdAt, 
            when);
    }
}
