package com.springbloom.domain.port.in;

import java.math.BigDecimal;
import java.util.List;

import com.springbloom.domain.model.ProductType;
import com.springbloom.domain.model.Quotation;

/**
 * Turns a customer's request into a priced, persisted quotation. The command
 * speaks species_key rather than ids, because that is the vocabulary both the
 * vision model and the agent already use.
 */
public interface RequestQuotationUseCase {

    /**
     * @throws com.springbloom.domain.service.UnknownSpeciesException if a species_key is not in the catalog
     * @throws com.springbloom.domain.service.UnavailableSpeciesException if a species cannot be sold in the amount asked
     */
    Quotation requestQuotation(RequestQuotationCommand command);

    /** sessionKey ties the quotation to an anonymous chat; there is no customer yet. */
    record RequestQuotationCommand(String sessionKey, List<LineRequest> lines) {

        public RequestQuotationCommand {

            if (sessionKey == null || sessionKey.isBlank()) {
                throw new IllegalArgumentException("A session key is required");
            }

            if (lines == null || lines.isEmpty()) {
                throw new IllegalArgumentException("A quotation needs at least one line");
            }
            lines = List.copyOf(lines);
        }
    }

    /**
     * One requested line. The discount rule is not re-checked here: QuotationItem
     * is the single place that enforces it, so a bad combination fails once, in
     * the domain, rather than differently at each entry point.
     */
    record LineRequest(
            ProductType productType,
            BigDecimal discountPercentage,
            List<SpeciesRequest> species) {

        public LineRequest {

            if (productType == null) {
                throw new IllegalArgumentException("productType is required");
            }

            if (species == null || species.isEmpty()) {
                throw new IllegalArgumentException("A quotation line needs at least one species");
            }

            species = List.copyOf(species);
        }
    }

    record SpeciesRequest(String speciesKey, int quantity) {

        public SpeciesRequest {
            
            if (speciesKey == null || speciesKey.isBlank()) {
                throw new IllegalArgumentException("speciesKey is required");
            }

            if (quantity <= 0) {
                throw new IllegalArgumentException("quantity must be positive: " + quantity);
            }
        }
    }
}
