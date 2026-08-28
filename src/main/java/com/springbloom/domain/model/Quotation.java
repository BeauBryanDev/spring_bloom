package com.springbloom.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.springbloom.domain.model.vo.Money;

/**
 * A priced quotation. Totals are derived from the lines rather than passed in,
 * so the three money columns on the quotation table can never disagree with the
 * items underneath them.
 *
 */
public record Quotation(
        Long id,
        String quotationNumber,
        Long customerId,
        UUID conversationId,
        QuotationStatus status,
        List<QuotationItem> items,
        Money subtotal,
        Money discountAmount,
        Money totalAmount,
        Instant validUntil ) {

    public Quotation {

        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("A quotation needs at least one line");
        }
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        items = List.copyOf(items);
    }

    /** Money adds up bottom-up: line totals, then lines, then the quotation. */
    public static Quotation of(List<QuotationItem> items) {

        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("A quotation needs at least one line");
        }

        Money subtotal = items.stream()
                .map(QuotationItem::composedSubtotal)
                .reduce(Money.ZERO, Money::plus);

        Money total = items.stream()
                .map(QuotationItem::subtotal)
                .reduce(Money.ZERO, Money::plus);

        return new Quotation(null, null, null, null, QuotationStatus.DRAFT,
                items, subtotal, subtotal.minus(total), total, null);
    }

    public boolean persisted() {
        return id != null;
    }

    public Optional<String> number() {
        return Optional.ofNullable(quotationNumber);
    }

    /** Called once per insert attempt: a rejected number is replaced, not patched. */
    public Quotation withNumber(String number) {

        return new Quotation(id, number, customerId, conversationId, status,
                items, subtotal, discountAmount, totalAmount, validUntil);
    }

    public Quotation withId(Long assignedId) {
        
        return new Quotation(assignedId, quotationNumber, customerId, conversationId, status,
                items, subtotal, discountAmount, totalAmount, validUntil);
    }

    public Quotation withConversation(UUID conversation) {
        return new Quotation(id, quotationNumber, customerId, conversation, status,
                items, subtotal, discountAmount, totalAmount, validUntil);
    }

    public Quotation withCustomer(Long customer) {
        return new Quotation(id, quotationNumber, customer, conversationId, status,
                items, subtotal, discountAmount, totalAmount, validUntil);
    }

    public Quotation withValidUntil(Instant until) {
        return new Quotation(id, quotationNumber, customerId, conversationId, status,
                items, subtotal, discountAmount, totalAmount, until);
    }

    public Quotation withStatus(QuotationStatus newStatus) {
        return new Quotation(id, quotationNumber, customerId, conversationId, newStatus,
                items, subtotal, discountAmount, totalAmount, validUntil);
    }

    public int totalStems() {
        return items.stream().mapToInt(QuotationItem::totalStems).sum();
    }
}
