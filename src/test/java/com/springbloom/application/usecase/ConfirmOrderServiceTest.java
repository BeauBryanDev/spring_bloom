package com.springbloom.application.usecase;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import com.springbloom.domain.model.FlowerOrder;
import com.springbloom.domain.model.FlowerStock;
import com.springbloom.domain.model.FlowerStockStatus;
import com.springbloom.domain.model.OrderItem;
import com.springbloom.domain.model.OrderItemSpecies;
import com.springbloom.domain.model.OrderStatus;
import com.springbloom.domain.model.Payment;
import com.springbloom.domain.model.PaymentStatus;
import com.springbloom.domain.model.ProductType;
import com.springbloom.domain.model.vo.Money;
import com.springbloom.domain.port.out.FlowerOrderRepository;
import com.springbloom.domain.port.out.FlowerStockRepository;
import com.springbloom.domain.port.out.PaymentPort;
import com.springbloom.domain.port.out.PaymentPort.Kind;
import com.springbloom.domain.port.out.PaymentPort.PaymentEvent;
import com.springbloom.domain.port.out.PaymentRepository;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConfirmOrderServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-26T15:00:00Z");

    private PaymentPort paymentPort;
    private PaymentRepository payments;
    private FlowerOrderRepository orders;
    private FlowerStockRepository stock;
    private ConfirmOrderService service;

    @BeforeEach
    void setUp() {
        paymentPort = mock(PaymentPort.class);
        payments = mock(PaymentRepository.class);
        orders = mock(FlowerOrderRepository.class);
        stock = mock(FlowerStockRepository.class);
        service = new ConfirmOrderService(paymentPort, payments, orders, stock,
                Clock.fixed(NOW, ZoneId.of("America/Bogota")), mock(PlatformTransactionManager.class));

        when(paymentPort.readNotification("body", "sig"))
                .thenReturn(new PaymentEvent(Kind.PAID, "cs_1", "pi_1"));
        when(payments.findBySessionId("cs_1")).thenReturn(Optional.of(payment(PaymentStatus.PENDING)));
        when(orders.findById(5L)).thenReturn(Optional.of(order(OrderStatus.PENDING)));
    }

    private static Payment payment(PaymentStatus status) {
        return new Payment(3L, "PAY-x", 5L, "cs_1", null, status, Money.of("13000"), "COP", null, null);
    }

    private static FlowerOrder order(OrderStatus status) {
        OrderItem item = new OrderItem(ProductType.INDIVIDUAL, null, List.of(
                new OrderItemSpecies(1L, "Rosa", 10, Money.of("1000"), Money.of("10000")),
                new OrderItemSpecies(2L, "Lirio", 3, Money.of("1000"), Money.of("3000"))), Money.of("13000"));
        return new FlowerOrder(5L, "ORD-20260826-54321", 7L, 9L, status, List.of(item),
                Money.of("13000"), Money.ZERO, Money.of("13000"), NOW);
    }

    private static FlowerStock stock(long speciesId, FlowerStockStatus status, int quantity) {
        return new FlowerStock(speciesId, speciesId, status, quantity, null, Money.of("1000"), BigDecimal.ONE, NOW);
    }

    @Test
    @DisplayName("a paid notification decrements IN_STOCK species only, then confirms the order")
    void confirms() {
        when(stock.findBySpeciesId(1L)).thenReturn(Optional.of(stock(1L, FlowerStockStatus.IN_STOCK, 50)));
        when(stock.findBySpeciesId(2L)).thenReturn(Optional.of(stock(2L, FlowerStockStatus.IMPORT_ON_REQUEST, 0)));
        when(stock.decrementIfAvailable(1L, 10)).thenReturn(true);

        service.handleNotification("body", "sig");

        verify(stock).decrementIfAvailable(1L, 10);
        verify(stock, never()).decrementIfAvailable(eq(2L), anyInt());
        verify(payments).markPaid(3L, "pi_1", null, NOW);
        verify(orders).updateStatus(5L, OrderStatus.CONFIRMED);
    }

    @Test
    @DisplayName("a retried notification for a settled payment does nothing")
    void retryIsNoop() {
        when(payments.findBySessionId("cs_1")).thenReturn(Optional.of(payment(PaymentStatus.PAID)));

        service.handleNotification("body", "sig");

        verify(stock, never()).decrementIfAvailable(anyLong(), anyInt());
        verify(orders, never()).updateStatus(anyLong(), any());
    }

    @Test
    @DisplayName("stock that ran out is recorded on the payment and the order stays PENDING")
    void shortStock() {
        when(stock.findBySpeciesId(1L)).thenReturn(Optional.of(stock(1L, FlowerStockStatus.IN_STOCK, 5)));
        when(stock.decrementIfAvailable(1L, 10)).thenReturn(false);

        service.handleNotification("body", "sig");

        verify(payments).markPaid(3L, "pi_1", ConfirmOrderService.SHORT_STOCK_NOTE, NOW);
        verify(orders, never()).updateStatus(anyLong(), any());
    }

    @Test
    @DisplayName("a payment for a session we do not know is ignored")
    void unknownSession() {
        when(payments.findBySessionId("cs_1")).thenReturn(Optional.empty());

        service.handleNotification("body", "sig");

        verify(payments, never()).markPaid(anyLong(), any(), any(), any());
        verify(orders, never()).updateStatus(anyLong(), any());
    }

    @Test
    @DisplayName("an expired checkout ends a pending payment and leaves the order payable")
    void expiredCheckout() {
        when(paymentPort.readNotification("body", "sig"))
                .thenReturn(new PaymentEvent(Kind.EXPIRED, "cs_1", null));

        service.handleNotification("body", "sig");

        verify(payments).markEnded(3L, PaymentStatus.EXPIRED);
        verify(orders, never()).updateStatus(anyLong(), any());
    }
}
