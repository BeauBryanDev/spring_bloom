package com.springbloom.application.usecase;

import java.time.Clock;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.springbloom.domain.model.Complaint;
import com.springbloom.domain.model.ComplaintType;
import com.springbloom.domain.model.FlowerOrder;
import com.springbloom.domain.model.Payment;
import com.springbloom.domain.model.Shipment;
import com.springbloom.domain.port.in.SubmitComplaintUseCase;
import com.springbloom.domain.port.out.ComplaintRepository;
import com.springbloom.domain.port.out.ConversationRepository;
import com.springbloom.domain.port.out.FlowerOrderRepository;
import com.springbloom.domain.port.out.PaymentRepository;
import com.springbloom.domain.port.out.ShipmentRepository;

import lombok.RequiredArgsConstructor;

/**
 * Orchestration only: check the claim is about an order, that the order is the
 * verified customer's and paid, that the claim makes sense for the delivery
 * state, then record it. A late claim is
 * still recorded, with a note for the human who reviews it.
 */
@Service
@RequiredArgsConstructor
public class SubmitComplaintService implements SubmitComplaintUseCase {

    private static final Logger log = LoggerFactory.getLogger(SubmitComplaintService.class);

    /** What can be claimed about an order that has not been delivered. */
    private static final Set<ComplaintType> BEFORE_DELIVERY =
            EnumSet.of(ComplaintType.NOT_DELIVERED, ComplaintType.LATE_DELIVERY);

    /** Claims about the condition of what arrived: the 24-hour window applies. */
    private static final Set<ComplaintType> WINDOWED =
            EnumSet.of(ComplaintType.DAMAGED_FLOWERS, 
                        ComplaintType.WRONG_ITEM);

    private final ConversationRepository conversations;
    private final FlowerOrderRepository orders;
    private final ShipmentRepository shipments;
    private final PaymentRepository payments;
    private final ComplaintRepository complaints;
    private final Clock clock;

    @Override
    public SubmitResult submit(SubmitComplaintCommand command) {
        if (command.type() == ComplaintType.OTHER) {
            return SubmitResult.refused(Refusal.NOT_ABOUT_AN_ORDER);
        }

        var conversation = conversations.findOrStart(command.sessionKey());

        Long customerId = conversation.customer().orElse(null);
        if (customerId == null) {

            return SubmitResult.refused(Refusal.NOT_VERIFIED);
        }

        // Someone else's order and an unknown one get the same refusal.
        Optional<FlowerOrder> order = orders.findByNumber(command.orderNumber())
                .filter(found -> customerId.equals(found.customerId()));

        if (order.isEmpty()) {
            log.warn("Session {} tried to claim on order {} it has not verified",
                    command.sessionKey(),
                     command.orderNumber());

            return SubmitResult.refused(Refusal.NOT_VERIFIED);
        }

        Long orderId = order.get().id();

        // Checked on the payment, not the order status: a paid order that hit short
        // stock stays PENDING, and that customer has the best reason to complain.
        if (payments.findByOrderId(orderId).stream().noneMatch(Payment::isPaid)) {
            return SubmitResult.refused(Refusal.NOT_PAID);
        }

        Optional<Shipment> shipment = shipments.findByOrderId(orderId);
        boolean delivered = shipment.map(Shipment::delivered).orElse(false);

        if (!delivered && !BEFORE_DELIVERY.contains(command.type())) {
            return SubmitResult.refused(Refusal.NOT_DELIVERED_YET);
        }

        Optional<Complaint> alreadyOpen = complaints.findByOrderId(orderId).stream()
                .filter(existing -> existing.type() == command.type() && existing.open())
                .findFirst();

        if (alreadyOpen.isPresent()) {

            return SubmitResult.alreadyOpen(alreadyOpen.get());
        }

        String description = command.description().trim();
        if (WINDOWED.contains(command.type())) {

            description = withLateNote(description, shipment);
        }

        Complaint saved = complaints.save(Complaint.filed(command.type(), description)
                .withConversation(conversation.id())
                .withOrder(orderId)
                .withCustomer(customerId));

        log.info("Session {} filed complaint {} on order {}",
                command.sessionKey(), saved.complaintNumber(), command.orderNumber());

        return SubmitResult.filed(saved);
    }

    /** Adds a line for the reviewer when the claim came after the report window. */
    private String withLateNote(String description, Optional<Shipment> shipment) {
        Duration elapsed = shipment.filter(Shipment::delivered)
                .map(delivered -> Duration.between(delivered.deliveredAt(), clock.instant()))
                .orElse(Duration.ZERO);

        if (elapsed.compareTo(LookupOrderService.REPORT_WINDOW) <= 0) {
            return description;
        }

        String note = "\n[Fuera de la ventana de 24 h: entregado hace " + elapsed.toHours() + " h]";
        int room = Complaint.MAX_DESCRIPTION - note.length();
        String base = description.length() > room ? description.substring(0, room) : description;
        
        return base + note;
    }
}
