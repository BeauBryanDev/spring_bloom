package com.springbloom.application.usecase;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.springbloom.domain.model.FlowerStock;
import com.springbloom.domain.model.FlowerStockStatus;
import com.springbloom.domain.model.vo.Money;
import com.springbloom.domain.port.in.UpdateStockUseCase.UpdateStockCommand;
import com.springbloom.domain.port.out.FlowerStockRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UpdateStockServiceTest {

    private static final Instant THEN = Instant.parse("2026-08-01T10:00:00Z");

    private FlowerStockRepository stock;
    private UpdateStockService service;

    @BeforeEach
    void setUp() {
        stock = mock(FlowerStockRepository.class);
        service = new UpdateStockService(stock);

        when(stock.findBySpeciesKey("rose")).thenReturn(Optional.of(new FlowerStock(
                5L, 11L, FlowerStockStatus.IN_STOCK, 40, null,
                Money.of("4000"), BigDecimal.ONE, THEN)));
        when(stock.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    private static UpdateStockCommand command(int quantity, String multiplier) {
        return new UpdateStockCommand("rose", FlowerStockStatus.IN_STOCK, quantity, null,
                new BigDecimal("4500"), new BigDecimal(multiplier));
    }

    @Test
    @DisplayName("an edit keeps the row's identity and replaces what the admin changed")
    void updatesTheRow() {
        service.update(command(25, "1.00"));

        ArgumentCaptor<FlowerStock> saved = ArgumentCaptor.forClass(FlowerStock.class);
        verify(stock).save(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo(5L);
        assertThat(saved.getValue().getSpeciesId()).isEqualTo(11L);
        assertThat(saved.getValue().getQuantity()).isEqualTo(25);
        assertThat(saved.getValue().getBasePrice()).isEqualTo(Money.of("4500"));
    }

    @Test
    @DisplayName("an unknown species key is refused, and nothing is created")
    void unknownSpecies() {
        when(stock.findBySpeciesKey("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(new UpdateStockCommand("nope",
                FlowerStockStatus.IN_STOCK, 1, null, BigDecimal.TEN, BigDecimal.ONE)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(stock, never()).save(any());
    }

    @Test
    @DisplayName("a negative quantity fails in the domain before save")
    void negativeQuantity() {
        assertThatThrownBy(() -> service.update(command(-1, "1.00")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(stock, never()).save(any());
    }

    @Test
    @DisplayName("a non-positive multiplier fails in the domain before save")
    void badMultiplier() {
        assertThatThrownBy(() -> service.update(command(5, "0")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(stock, never()).save(any());
    }

    @Test
    @DisplayName("the command rejects a blank key and missing fields on its own")
    void commandShape() {
        assertThatThrownBy(() -> new UpdateStockCommand(" ", FlowerStockStatus.IN_STOCK, 1, null,
                BigDecimal.TEN, BigDecimal.ONE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UpdateStockCommand("rose", null, 1, null,
                BigDecimal.TEN, BigDecimal.ONE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UpdateStockCommand("rose", FlowerStockStatus.IN_STOCK, 1, null,
                null, BigDecimal.ONE)).isInstanceOf(IllegalArgumentException.class);
    }
}
