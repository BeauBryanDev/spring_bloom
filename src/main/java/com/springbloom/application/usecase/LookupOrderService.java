package com.springbloom.application.usecase;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Stream;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.springbloom.domain.model.Customer;
import com.springbloom.domain.model.FlowerOrder;
import com.springbloom.domain.model.OrderItemSpecies;
import com.springbloom.domain.model.Shipment;
import com.springbloom.domain.model.ShipmentStatus;
import com.springbloom.domain.port.in.LookupOrderUseCase;
import com.springbloom.domain.port.out.ConversationRepository;
import com.springbloom.domain.port.out.CustomerRepository;
import com.springbloom.domain.port.out.FlowerOrderRepository;
import com.springbloom.domain.port.out.ShipmentRepository;
import com.springbloom.domain.service.ColombianPhone;

import lombok.RequiredArgsConstructor;

/**
 * Order number plus phone proves ownership. A match ties the conversation to the
 * customer; a miss counts against the session, and three misses lock it for a
 * while. Not @Transactional on purpose: every repository call is its own short
 * transaction and nothing here needs them to be one.
 */
@Service
@RequiredArgsConstructor
public class LookupOrderService implements LookupOrderUseCase {

    private static final Logger log = LoggerFactory.getLogger(LookupOrderService.class);

    static final int MAX_FAILURES = 3;
    static final Duration LOCK_WINDOW = Duration.ofMinutes(15);
    static final Duration REPORT_WINDOW = Duration.ofHours(24);

    private final FlowerOrderRepository orders;
    private final CustomerRepository customers;
    private final ShipmentRepository shipments;
    private final ConversationRepository conversations;
    private final Clock clock;

    /** Failure times per session and per IP, so rotating session keys does not reset the lock. Lives in memory: a restart forgives everyone, which is acceptable. */
    private final Map<String, Deque<Instant>> failures = new HashMap<>();

    @Override
    public OrderLookup lookup(LookupOrderCommand command) {
        if (locked(sessionKeyOf(command)) || (ipKeyOf(command) != null && locked(ipKeyOf(command)))) {
            log.warn("Session {} is locked out of order lookups", command.sessionKey());
            return OrderLookup.refused(Outcome.LOCKED);
        }

        // Not a guess, just a typo: it must not cost the customer an attempt.
        if (ColombianPhone.normalize(command.phone()).isEmpty()) {
            return OrderLookup.refused(Outcome.INVALID_PHONE);
        }

        Optional<FlowerOrder> order = orders.findByNumber(command.orderNumber());
        Optional<Customer> owner = order.flatMap(found -> customers.findById(found.customerId()));

        if (owner.isEmpty() || !ColombianPhone.sameNumber(owner.get().phone(), command.phone())) {
            recordFailure(sessionKeyOf(command));
            if (ipKeyOf(command) != null) {
                recordFailure(ipKeyOf(command));
            }
            return OrderLookup.refused(Outcome.NOT_MATCHED);
        }

        // The IP's failures are left to expire: one real order must not reset an attacker's count.
        clearFailures(sessionKeyOf(command));

        conversations.assignCustomer(
                conversations.findOrStart(command.sessionKey()).id(), owner.get().id());

        log.info("Session {} verified order {}", command.sessionKey(), command.orderNumber());

        return OrderLookup.verified(describe(order.get()));
    }

    private VerifiedOrder describe(FlowerOrder order) {
        Optional<Shipment> shipment = shipments.findByOrderId(order.id());

        ShipmentStatus shipmentStatus = shipment.map(Shipment::status).orElse(null);
        Instant deliveredAt = shipment.filter(Shipment::delivered)
                .map(Shipment::deliveredAt)
                .orElse(null);

        Long hoursSinceDelivery = null;
        boolean withinWindow = false;
        if (deliveredAt != null) {
            Duration elapsed = Duration.between(deliveredAt, clock.instant());
            hoursSinceDelivery = elapsed.toHours();
            withinWindow = elapsed.compareTo(REPORT_WINDOW) <= 0;
        }

        List<String> items = order.items().stream()
                .flatMap(item -> item.isArrangement()
                        ? Stream.of(
                                item.creation().units() + " x " + item.creation().nameSnapshot())
                        : item.species().stream().map(LookupOrderService::line))
                .toList();

        return new VerifiedOrder(order.orderNumber(),
                order.status(), 
                order.totalAmount(),
                order.createdAt(), 
                shipmentStatus,
                deliveredAt, 
                hoursSinceDelivery,
                withinWindow,
                items);
    }

    private static String line(OrderItemSpecies species) {
        return species.quantity() + " x " + species.commonNameSnapshot();
    }

    private static String sessionKeyOf(LookupOrderCommand command) {
        return "session:" + command.sessionKey();
    }

    private static String ipKeyOf(LookupOrderCommand command) {
        return command.clientIp() == null || command.clientIp().isBlank() ? null : "ip:" + command.clientIp();
    }

    private synchronized boolean locked(String sessionKey) {
        Deque<Instant> recent = failures.get(sessionKey);
        if (recent == null) {
            return false;
        }
        Instant cutoff = clock.instant().minus(LOCK_WINDOW);
        while (!recent.isEmpty() && recent.peekFirst().isBefore(cutoff)) {
            recent.pollFirst();
        }
        if (recent.isEmpty()) {
            failures.remove(sessionKey);
            return false;
        }
        return recent.size() >= MAX_FAILURES;
    }

    private synchronized void recordFailure(String sessionKey) {
        failures.computeIfAbsent(sessionKey, key -> new ArrayDeque<>()).addLast(clock.instant());
    }

    private synchronized void clearFailures(String sessionKey) {
        failures.remove(sessionKey);
    }
}
