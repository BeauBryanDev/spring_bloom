package com.springbloom.domain.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.springbloom.domain.model.Complaint;

/**
 * Stores customer claims. Numbering is the adapter's business, as with
 * quotations: it generates one, attempts the insert, and retries the collision.
 */
public interface ComplaintRepository {

    Complaint save(Complaint complaint);

    Optional<Complaint> findByNumber(String complaintNumber);

    Optional<Complaint> findById(Long id);

    List<Complaint> findByOrderId(Long orderId);

    List<Complaint> findByConversationId(UUID conversationId);

    /** Every claim, newest first, for the admin review screen. */
    List<Complaint> findAll();

    /**
     * Resolves a customer-facing order number to its id.
     *
     * This sits here as a stopgap: FlowerOrderRepository does not exist yet, and
     * mapping the whole order aggregate just to attach a claim would be a large
     * detour. Move it when the order half is built.
     */
    Optional<Long> findOrderIdByNumber(String orderNumber);
}
