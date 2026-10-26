package com.pydlove.regent.delivery.domain.model;

import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.shared.domain.DomainException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** 需求详细设计中的一个模块，以及该模块的研发负责人和设计内容。 */
public class RequirementDesignModule {

    private RequirementDesignModuleId id;
    private final RequirementDeliveryId deliveryId;
    private String name;
    private String description;
    private UserId assigneeId;
    private DesignModuleStatus status;
    private String designSummary;
    private String designContent;
    private final UserId createdBy;
    private final Instant createdAt;
    private Instant updatedAt;

    private RequirementDesignModule(
            RequirementDesignModuleId id,
            RequirementDeliveryId deliveryId,
            String name,
            String description,
            UserId assigneeId,
            DesignModuleStatus status,
            String designSummary,
            String designContent,
            UserId createdBy,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.deliveryId = requireNotNull(deliveryId, "需求交付");
        this.name = requireText(name, "模块名称", 160);
        this.description = optionalText(description, 8000);
        this.assigneeId = requireNotNull(assigneeId, "模块研发负责人");
        this.status = requireNotNull(status, "模块设计状态");
        this.designSummary = optionalText(designSummary, 4000);
        this.designContent = optionalText(designContent, 60000);
        this.createdBy = requireNotNull(createdBy, "创建人");
        this.createdAt = requireNotNull(createdAt, "创建时间").truncatedTo(ChronoUnit.MILLIS);
        this.updatedAt = requireNotNull(updatedAt, "更新时间").truncatedTo(ChronoUnit.MILLIS);
    }

    public static RequirementDesignModule create(
            RequirementDeliveryId deliveryId,
            String name,
            String description,
            UserId assigneeId,
            UserId createdBy) {
        Instant now = Instant.now();
        return new RequirementDesignModule(
                null, deliveryId, name, description, assigneeId,
                DesignModuleStatus.PENDING, null, null, createdBy, now, now);
    }

    public static RequirementDesignModule restore(
            RequirementDesignModuleId id,
            RequirementDeliveryId deliveryId,
            String name,
            String description,
            UserId assigneeId,
            DesignModuleStatus status,
            String designSummary,
            String designContent,
            UserId createdBy,
            Instant createdAt,
            Instant updatedAt) {
        return new RequirementDesignModule(
                id, deliveryId, name, description, assigneeId, status,
                designSummary, designContent, createdBy, createdAt, updatedAt);
    }

    public void updateAssignment(String name, String description, UserId assigneeId) {
        this.name = requireText(name, "模块名称", 160);
        this.description = optionalText(description, 8000);
        this.assigneeId = requireNotNull(assigneeId, "模块研发负责人");
        if (status == DesignModuleStatus.SUBMITTED) {
            status = DesignModuleStatus.REVISION_REQUIRED;
        }
        touch();
    }

    public void saveDesign(String summary, String content) {
        requireEditable();
        this.designSummary = optionalText(summary, 4000);
        this.designContent = optionalText(content, 60000);
        touch();
    }

    public void submit() {
        requireEditable();
        status = DesignModuleStatus.SUBMITTED;
        touch();
    }

    public void reopen() {
        if (status == DesignModuleStatus.SUBMITTED) {
            status = DesignModuleStatus.REVISION_REQUIRED;
            touch();
        }
    }

    public void requireEditable() {
        if (status == DesignModuleStatus.SUBMITTED) {
            throw new DomainException("模块设计已提交；如需修改，请先由技术负责人退回");
        }
    }

    public void assignIdentity(RequirementDesignModuleId assigned) {
        if (id != null) {
            throw new DomainException("设计模块已有 ID，不能重复分配");
        }
        id = requireNotNull(assigned, "设计模块 ID");
    }

    private void touch() {
        updatedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    private static String requireText(String value, String field, int max) {
        if (value == null || value.isBlank()) {
            throw new DomainException(field + "不能为空");
        }
        String text = value.trim();
        if (text.length() > max) {
            throw new DomainException(field + "不能超过 " + max + " 个字符");
        }
        return text;
    }

    private static String optionalText(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String text = value.trim();
        if (text.length() > max) {
            throw new DomainException("文本不能超过 " + max + " 个字符");
        }
        return text;
    }

    private static <T> T requireNotNull(T value, String field) {
        if (value == null) {
            throw new DomainException(field + "不能为空");
        }
        return value;
    }

    public RequirementDesignModuleId getId() {
        return id;
    }

    public RequirementDeliveryId getDeliveryId() {
        return deliveryId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public UserId getAssigneeId() {
        return assigneeId;
    }

    public DesignModuleStatus getStatus() {
        return status;
    }

    public String getDesignSummary() {
        return designSummary;
    }

    public String getDesignContent() {
        return designContent;
    }

    public UserId getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
