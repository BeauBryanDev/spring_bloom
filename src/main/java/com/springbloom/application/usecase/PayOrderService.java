package com.springbloom.application.usecase;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.springbloom.domain.model.FlowerOrder;
import com.springbloom.domain.model.OrderStatus;
import com.springbloom.domain.model.Payment;
import com.springbloom.domain.port.in.PayOrderUseCase;
import com.springbloom.domain.port.out.FlowerOrderRepository;
import com.springbloom.domain.port.out.PaymentPort;
import com.springbloom.domain.port.out.PaymentPort.CheckoutRequest;
import com.springbloom.domain.port.out.PaymentPort.CheckoutSession;
import com.springbloom.domain.port.out.PaymentRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PayOrderService implements PayOrderUseCase {

    private static final Logger log = LoggerFactory.getLogger(PayOrderService.class);
    private static final String CURRENCY = "COP";

    private final FlowerOrderRepository orders;
    private final PaymentRepository payments;
    private final PaymentPort paymentPort;

    @Override
    public Optional<OrderView> view(String orderNumber) {
        return find(orderNumber).map(this::toView);
    }

    @Override
    public CheckoutLink startCheckout(String orderNumber) {
        FlowerOrder order = find(orderNumber)
                .orElseThrow(() -> new IllegalArgumentException("No encontramos ese pedido"));

        OrderView view = toView(order);
        if (view.paid()) {
            throw new IllegalStateException("Ese pedido ya fue pagado");
        }
        if (!view.payable()) {
            throw new IllegalStateException("Ese pedido ya no admite pago");
        }

        String reference = "PAY-" + UUID.randomUUID().toString().replace("-", "");
        CheckoutSession session = paymentPort.createCheckout(new CheckoutRequest(
                order.orderNumber(), reference, order.totalAmount(), CURRENCY,
                "Pedido " + order.orderNumber() + " - Springbloom"));

        payments.save(Payment.pending(reference, order.id(), session.sessionId(),
                order.totalAmount(), CURRENCY));

        log.info("Checkout started for {} as {}", order.orderNumber(), reference);
        return new CheckoutLink(session.redirectUrl());
    }

    private Optional<FlowerOrder> find(String orderNumber) {
        if (orderNumber == null || orderNumber.isBlank()) {
            return Optional.empty();
        }
        return orders.findByNumber(orderNumber.trim().toUpperCase(Locale.ROOT));
    }

    private OrderView toView(FlowerOrder order) {
        boolean paid = payments.existsPaidForOrder(order.id());
        boolean payable = order.status() == OrderStatus.PENDING && !paid;

        List<ViewLine> lines = order.items().stream()
                .flatMap(item -> item.species().stream()
                        .map(line -> new ViewLine(item.productType(),
                                line.commonNameSnapshot(),
                                line.quantity(),
                                line.unitPriceSnapshot(),
                                line.lineTotal())))
                .toList();

        return new OrderView(order.orderNumber(),
                order.status(),
                paid, payable, lines,
                order.subtotal(),
                order.discountAmount(),
                order.totalAmount());
    }
}
