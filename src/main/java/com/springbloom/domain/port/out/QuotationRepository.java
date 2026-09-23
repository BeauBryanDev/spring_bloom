package com.springbloom.domain.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.springbloom.domain.model.Quotation;
import com.springbloom.domain.model.QuotationSummary;

/**
 * Stores and reads quotations as whole documents. A quotation is one aggregate:
 * lines and their species are written through the root, never separately.
 */
public interface QuotationRepository {

    /**
     * Persists a composed quotation and returns it with its database id and the
     * quotation number the adapter settled on.
     *
     * The number is the adapter's business, not the caller's: it generates one,
     * attempts the insert, and retries on the UNIQUE collision the 5-digit
     * suffix makes routine.
     */
    Quotation save(Quotation quotation);

    Optional<Quotation> findByNumber(String quotationNumber);

    Optional<Quotation> findById(Long id);

    /** A conversation may produce several quotations, newest first. */
    List<Quotation> findByConversationId(UUID conversationId);

    /**
     * Header fields only, for the admin dashboard. Deliberately not List<Quotation>:
     * loading every document's full item graph to sum totals would be needless
     * work the aggregate view never asked for.
     */
    List<QuotationSummary> findAllSummaries();
}
