package com.springbloom.application.usecase;

import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.springbloom.domain.model.Complaint;
import com.springbloom.domain.port.in.FileComplaintUseCase;
import com.springbloom.domain.port.out.ComplaintRepository;
import com.springbloom.domain.port.out.ConversationRepository;

import lombok.RequiredArgsConstructor;

/**
 * Orchestration only: tie the claim to its conversation, resolve the order
 * number if the customer gave a real one, persist. It never decides an outcome.
 */
@Service
@RequiredArgsConstructor
public class FileComplaintService implements FileComplaintUseCase {

    private static final Logger log = LoggerFactory.getLogger(FileComplaintService.class);

    private final ComplaintRepository complaints;
    private final ConversationRepository conversations;

    @Override
    public Complaint file(FileComplaintCommand command) {
        UUID conversationId = conversations.findOrStart(command.sessionKey()).id();

        // An order number we cannot find is not a reason to lose the claim: the
        // customer may be misremembering it, and the shop can still investigate.
        Optional<Long> orderId = command.order().flatMap(complaints::findOrderIdByNumber);
        if (command.order().isPresent() && orderId.isEmpty()) {
            log.info("Session {} filed a claim against unknown order {}",
                    command.sessionKey(), command.orderNumber());
        }

        Complaint filed = Complaint.filed(command.type(), command.description())
                .withConversation(conversationId)
                .withOrder(orderId.orElse(null));

        Complaint saved = complaints.save(filed);

        log.info("Session {} filed complaint {} of type {}",
                command.sessionKey(), saved.complaintNumber(), saved.type());

        return saved;
    }
}
