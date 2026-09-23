package com.springbloom.domain.port.in;

import java.util.List;
import java.util.Map;

import com.springbloom.domain.model.FlowerStockStatus;
import com.springbloom.domain.model.QuotationStatus;
import com.springbloom.domain.model.QuotationSummary;
import com.springbloom.domain.model.vo.Money;

/**
 * Read-only sales overview for the admin dashboard. Built on quotation and
 * stock data only - the order half does not exist yet, so nothing here reads
 * flower_order.
 */
public interface DashboardQueryUseCase {

    Summary summarize();

    /** Species and stock joined for display, the same split ListCatalogService uses. */
    record StockAlert(String speciesKey, String commonName, FlowerStockStatus status, int quantity) {
    }

    /**
     * quotationsByStatus and totalByStatus share keys with QuotationStatus so
     * the template can show a count and a sum side by side without a second pass.
     *
     * lowStock is every IN_STOCK species running low on hand; importOnRequest is
     * every species awaiting a restock or import - the two views a store owner
     * actually needs, not a dump of all 90 rows.
     */
    record Summary(
            Map<QuotationStatus, Long> quotationsByStatus,
            Map<QuotationStatus, Money> totalByStatus,
            Money totalQuoted,
            List<QuotationSummary> recentQuotations,
            List<StockAlert> lowStock,
            List<StockAlert> importOnRequest) {
    }
}
