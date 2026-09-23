package com.springbloom.domain.model;

import java.time.Instant;

import com.springbloom.domain.model.vo.Money;

/**
 * One row for the admin dashboard: header fields only, no lines. Separate from
 * Quotation itself because the dashboard aggregates over dozens of documents
 * and has no business loading each one's full item graph to do it.
 */
public record QuotationSummary(
        String quotationNumber,
        QuotationStatus status,
        Money totalAmount,
        Instant createdAt) {
}
