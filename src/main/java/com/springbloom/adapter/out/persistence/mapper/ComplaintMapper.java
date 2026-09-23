package com.springbloom.adapter.out.persistence.mapper;

import com.springbloom.adapter.out.persistence.entity.ComplaintEntity;
import com.springbloom.domain.model.Complaint;

/** Both directions, static, no Spring. */
public class ComplaintMapper {

    private ComplaintMapper() {
    }

    public static ComplaintEntity toEntity(Complaint complaint) {
        ComplaintEntity entity = new ComplaintEntity();
        entity.setId(complaint.id());
        entity.setComplaintNumber(complaint.complaintNumber());
        entity.setOrderId(complaint.orderId());
        entity.setCustomerId(complaint.customerId());
        entity.setConversationId(complaint.conversationId());
        entity.setType(complaint.type());
        entity.setStatus(complaint.status());
        entity.setDescription(complaint.description());
        entity.setResolution(complaint.resolution());
        entity.setCreatedAt(complaint.createdAt());
        entity.setResolvedAt(complaint.resolvedAt());
        return entity;
    }

    public static Complaint toDomain(ComplaintEntity entity) {
        return new Complaint(
                entity.getId(),
                entity.getComplaintNumber(),
                entity.getOrderId(),
                entity.getCustomerId(),
                entity.getConversationId(),
                entity.getType(),
                entity.getStatus(),
                entity.getDescription(),
                entity.getResolution(),
                entity.getCreatedAt(),
                entity.getResolvedAt());
    }
}
