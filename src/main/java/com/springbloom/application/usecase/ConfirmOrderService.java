package com.springbloom.application.usecase;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.springbloom.domain.model.FlowerOrder;
import com.springbloom.domain.model.FlowerStock;
import com.springbloom.domain.model.FlowerStockStatus;
import com.springbloom.domain.model.OrderStatus;
import com.springbloom.domain.model.Payment;
import com.springbloom.domain.model.PaymentStatus;
import com.springbloom.domain.port.in.ConfirmOrderUseCase;
import com.springbloom.domain.port.out.FlowerOrderRepository;
import com.springbloom.domain.port.out.FlowerStockRepository;
import com.springbloom.domain.port.out.PaymentPort;
import com.springbloom.domain.port.out.PaymentPort.PaymentEvent;
import com.springbloom.domain.port.out.PaymentRepository;

/**
 * Settles an order from a verified payment notification. Payment, stock and
 * order status change in one transaction, so a failed decrement rolls the
 * others back rather than leaving a confirmed order that took no stock.
 */
@Service
public class ConfirmOrderService implements ConfirmOrderUseCase {

    private static final Logger log = LoggerFactory.getLogger(ConfirmOrderService.class);

    static final String SHORT_STOCK_NOTE = "Pagado sin stock suficiente: requiere reembolso o reposicion";
    static final String NOT_PENDING_NOTE = "Pagado cuando el pedido ya no estaba pendiente: revisar";

    private final PaymentPort paymentPort;
    private final PaymentRepository payments;
    private final FlowerOrderRepository orders;
    private final FlowerStockRepository stock;
    private final Clock storeClock;
    private final TransactionTemplate transaction;

    public ConfirmOrderService(
            PaymentPort paymentPort,
            PaymentRepository payments,
            FlowerOrderRepository orders,
            FlowerStockRepository stock,
            Clock storeClock,
            PlatformTransactionManager transactionManager) {

        this.paymentPort = paymentPort;
        this.payments = payments;
        this.orders = orders;
        this.stock = stock;
        this.storeClock = storeClock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Override
    public void handleNotification(String payload, String signature) {
        PaymentEvent event = paymentPort.readNotification(payload, signature);

        switch (event.kind()) {
            case PAID -> paid(event);
            case EXPIRED -> expired(event);
            case IGNORED -> log.debug("Ignoring a payment notification we do not act on");
        }
    }

    private void paid(PaymentEvent event) {
        Payment payment = payments.findBySessionId(event.sessionId()).orElse(null);
        if (payment == null) {
            log.warn("Paid notification for unknown checkout session {}", event.sessionId());
            return;
        }
        if (payment.isPaid()) {
            log.info("Payment {} already settled, ignoring the retry", payment.paymentReference());
            return;
        }

        FlowerOrder order = orders.findById(payment.orderId()).orElseThrow(
                () -> new IllegalStateException("Payment " + payment.paymentReference() + " has no order"));
        Instant now = storeClock.instant().truncatedTo(ChronoUnit.MICROS);

        if (order.status() != OrderStatus.PENDING) {
            log.error("Payment {} landed on {} which is {}", payment.paymentReference(),
                    order.orderNumber(), order.status());
            payments.markPaid(payment.id(), event.paymentIntentId(), NOT_PENDING_NOTE, now);
            return;
        }

        try {
            transaction.executeWithoutResult(status -> {
                takeStock(order);
                payments.markPaid(payment.id(), event.paymentIntentId(), null, now);
                orders.updateStatus(order.id(), OrderStatus.CONFIRMED);
            });
            log.info("{} confirmed by payment {}", order.orderNumber(), payment.paymentReference());

        } catch (InsufficientStockException shortfall) {
            // The money is real, so it is recorded; the order stays PENDING for a human.
            log.error("{} paid as {} but {} ran short: needs a refund or a restock",
                    order.orderNumber(), payment.paymentReference(), shortfall.getMessage());
            payments.markPaid(payment.id(), event.paymentIntentId(), SHORT_STOCK_NOTE, now);
        }
    }

    /**
     * Only IN_STOCK carries a quantity. A restock or an import is ordered to
     * demand and sits at zero, so there is nothing to decrement for it.
     */
    private void takeStock(FlowerOrder order) {
        for (Map.Entry<Long, Integer> need : order.stemsBySpecies().entrySet()) {
            FlowerStock row = stock.findBySpeciesId(need.getKey())
                    .orElseThrow(() -> new InsufficientStockException("species " + need.getKey()));

            if (row.getStatus() == FlowerStockStatus.IN_STOCK
                    && !stock.decrementIfAvailable(need.getKey(), need.getValue())) {
                throw new InsufficientStockException("species " + need.getKey());
            }
        }
    }

    private void expired(PaymentEvent event) {
        payments.findBySessionId(event.sessionId())
                .filter(payment -> payment.status() == PaymentStatus.PENDING)
                .ifPresent(payment -> payments.markEnded(payment.id(), PaymentStatus.EXPIRED));
    }

    private static final class InsufficientStockException extends RuntimeException {
        InsufficientStockException(String what) {
            super(what);
        }
    }
}
