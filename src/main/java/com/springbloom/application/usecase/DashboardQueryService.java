package com.springbloom.application.usecase;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springbloom.domain.model.FlowerSpecies;
import com.springbloom.domain.model.FlowerStock;
import com.springbloom.domain.model.FlowerStockStatus;
import com.springbloom.domain.model.QuotationStatus;
import com.springbloom.domain.model.QuotationSummary;
import com.springbloom.domain.model.vo.Money;
import com.springbloom.domain.port.in.DashboardQueryUseCase;
import com.springbloom.domain.port.out.FlowerSpeciesRepository;
import com.springbloom.domain.port.out.FlowerStockRepository;
import com.springbloom.domain.port.out.QuotationRepository;

/**
 * Aggregates quotation and stock data for the admin dashboard. Everything is
 * computed in memory: 20 quotations and 90 stock rows cost nothing to sum in
 * Java, and a real reporting need would earn a SQL aggregate later.
 */
@Service
public class DashboardQueryService implements DashboardQueryUseCase {

    /** Below this, an IN_STOCK species is flagged even though it is still sellable. */
    private static final int LOW_STOCK_THRESHOLD = 10;

    private final QuotationRepository quotationRepository;
    private final FlowerStockRepository stockRepository;
    private final FlowerSpeciesRepository speciesRepository;

    public DashboardQueryService(
            QuotationRepository quotationRepository,
            FlowerStockRepository stockRepository,
            FlowerSpeciesRepository speciesRepository) {

        this.quotationRepository = quotationRepository;
        this.stockRepository = stockRepository;
        this.speciesRepository = speciesRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Summary summarize() {
        List<QuotationSummary> quotations = quotationRepository.findAllSummaries();

        Map<QuotationStatus, Long> countsByStatus = new EnumMap<>(QuotationStatus.class);
        Map<QuotationStatus, Money> totalsByStatus = new EnumMap<>(QuotationStatus.class);
        for (QuotationStatus status : QuotationStatus.values()) {
            countsByStatus.put(status, 0L);
            totalsByStatus.put(status, Money.ZERO);
        }
        for (QuotationSummary q : quotations) {
            countsByStatus.merge(q.status(), 1L, Long::sum);
            totalsByStatus.merge(q.status(), q.totalAmount(), Money::plus);
        }

        Money totalQuoted = quotations.stream()
                .map(QuotationSummary::totalAmount)
                .reduce(Money.ZERO, Money::plus);

        List<QuotationSummary> recent = quotations.stream()
                .sorted(Comparator.comparing(QuotationSummary::createdAt).reversed())
                .limit(10)
                .toList();

        Map<Long, FlowerSpecies> speciesById = speciesRepository.findAll().stream()
                .collect(Collectors.toMap(FlowerSpecies::getId, Function.identity()));

        List<FlowerStock> stock = stockRepository.findAll();

        List<StockAlert> lowStock = stock.stream()
                .filter(s -> s.getStatus() == FlowerStockStatus.IN_STOCK
                        && s.getQuantity() < LOW_STOCK_THRESHOLD)
                .map(s -> toAlert(s, speciesById))
                .sorted(Comparator.comparing(StockAlert::quantity))
                .toList();

        List<StockAlert> importOnRequest = stock.stream()
                .filter(s -> s.getStatus() == FlowerStockStatus.IMPORT_ON_REQUEST)
                .map(s -> toAlert(s, speciesById))
                .sorted(Comparator.comparing(StockAlert::commonName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        return new Summary(countsByStatus, 
                totalsByStatus, 
                totalQuoted, 
                recent, lowStock, importOnRequest);
    }
              
    private StockAlert toAlert(FlowerStock stock, Map<Long, FlowerSpecies> speciesById) {
        FlowerSpecies species = speciesById.get(stock.getSpeciesId());
        String key = species != null ? species.getSpeciesKey() : "?";
        String name = species != null ? species.getCommonName() : "Especie desconocida";
        return new StockAlert(key, name, stock.getStatus(), stock.getQuantity());
    }
}
