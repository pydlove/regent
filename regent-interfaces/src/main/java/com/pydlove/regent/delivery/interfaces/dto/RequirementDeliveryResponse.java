package com.pydlove.regent.delivery.interfaces.dto;

import com.pydlove.regent.delivery.application.query.RequirementDeliveryView;
import com.pydlove.regent.delivery.domain.model.RequirementDelivery;
import com.pydlove.regent.delivery.domain.model.RequirementDesignAttachment;
import com.pydlove.regent.delivery.domain.model.RequirementDesignModule;

import java.time.Instant;
import java.util.List;

public record RequirementDeliveryResponse(
        long id,
        long initiativeId,
        long projectId,
        long requirementId,
        String requirementTitle,
        String initiativeTitle,
        String projectName,
        String stage,
        String status,
        String designSummary,
        String designContent,
        String outlineDesignSummary,
        String outlineDesignContent,
        Instant outlineDesignSubmittedAt,
        Long outlineDesignSubmittedBy,
        Instant createdAt,
        Instant updatedAt,
        List<DeliveryGateResponse> gates,
        List<DesignModuleResponse> modules,
        List<DesignAttachmentResponse> designAttachments) {

    public static RequirementDeliveryResponse from(RequirementDeliveryView view) {
        RequirementDelivery delivery = view.delivery();
        return new RequirementDeliveryResponse(
                delivery.getId().value(),
                delivery.getInitiativeId().value(),
                delivery.getProjectId().value(),
                delivery.getRequirementId().value(),
                view.requirementTitle(),
                view.initiativeTitle(),
                view.projectName(),
                delivery.getStage().name(),
                delivery.getStatus().name(),
                delivery.getDesignSummary(),
                delivery.getDesignContent(),
                delivery.getOutlineDesignSummary(),
                delivery.getOutlineDesignContent(),
                delivery.getOutlineDesignSubmittedAt(),
                delivery.getOutlineDesignSubmittedBy() == null
                        ? null
                        : delivery.getOutlineDesignSubmittedBy().value(),
                delivery.getCreatedAt(),
                delivery.getUpdatedAt(),
                view.gates().stream().map(DeliveryGateResponse::from).toList(),
                view.modules().stream().map(DesignModuleResponse::from).toList(),
                view.attachments().stream()
                        .map(DesignAttachmentResponse::from)
                        .toList());
    }

    public record DesignModuleResponse(
            long id,
            String name,
            String description,
            long assigneeId,
            String status,
            String designSummary,
            String designContent,
            Instant createdAt,
            Instant updatedAt) {

        public static DesignModuleResponse from(RequirementDesignModule module) {
            return new DesignModuleResponse(
                    module.getId().value(),
                    module.getName(),
                    module.getDescription(),
                    module.getAssigneeId().value(),
                    module.getStatus().name(),
                    module.getDesignSummary(),
                    module.getDesignContent(),
                    module.getCreatedAt(),
                    module.getUpdatedAt());
        }
    }

    public record DesignAttachmentResponse(
            long id,
            Long moduleId,
            String filename,
            String contentType,
            long sizeBytes,
            String contentUrl,
            long uploadedBy,
            Instant createdAt) {

        public static DesignAttachmentResponse from(
                RequirementDesignAttachment attachment) {
            return new DesignAttachmentResponse(
                    attachment.id().value(),
                    attachment.moduleId() == null
                            ? null
                            : attachment.moduleId().value(),
                    attachment.filename(),
                    attachment.contentType(),
                    attachment.sizeBytes(),
                    "/api/requirement-design-attachments/"
                            + attachment.id().value() + "/content?deliveryId="
                            + attachment.deliveryId().value(),
                    attachment.uploadedBy().value(),
                    attachment.createdAt());
        }
    }
}
