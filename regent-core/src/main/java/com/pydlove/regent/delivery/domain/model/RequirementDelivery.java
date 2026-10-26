package com.pydlove.regent.delivery.domain.model;

import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.requirement.domain.model.RequirementId;
import com.pydlove.regent.shared.domain.DomainException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** 一条需求的研发交付过程。只保存跨聚合 ID，不持有需求或任务对象。 */
public class RequirementDelivery {

    private RequirementDeliveryId id;
    private final InitiativeId initiativeId;
    private final ProjectId projectId;
    private final RequirementId requirementId;
    private final UserId createdBy;
    private DeliveryStage stage;
    private DeliveryStatus status;
    private String designSummary;
    private String designContent;
    private String outlineDesignSummary;
    private String outlineDesignContent;
    private Instant outlineDesignSubmittedAt;
    private UserId outlineDesignSubmittedBy;
    private final Instant createdAt;
    private Instant updatedAt;

    private RequirementDelivery(RequirementDeliveryId id,
                                InitiativeId initiativeId,
                                ProjectId projectId,
                                RequirementId requirementId,
                                UserId createdBy,
                                DeliveryStage stage,
                                DeliveryStatus status,
                                String designSummary,
                                String designContent,
                                String outlineDesignSummary,
                                String outlineDesignContent,
                                Instant outlineDesignSubmittedAt,
                                UserId outlineDesignSubmittedBy,
                                Instant createdAt,
                                Instant updatedAt) {
        this.id = id;
        this.initiativeId = requireNotNull(initiativeId, "立项 ID");
        this.projectId = requireNotNull(projectId, "项目 ID");
        this.requirementId = requireNotNull(requirementId, "需求 ID");
        this.createdBy = requireNotNull(createdBy, "创建人");
        this.stage = requireNotNull(stage, "交付阶段");
        this.status = requireNotNull(status, "交付状态");
        this.designSummary = optionalText(designSummary, 4000);
        this.designContent = optionalText(designContent, 60000);
        this.outlineDesignSummary = optionalText(outlineDesignSummary, 4000);
        this.outlineDesignContent = optionalText(outlineDesignContent, 60000);
        this.outlineDesignSubmittedAt = outlineDesignSubmittedAt;
        this.outlineDesignSubmittedBy = outlineDesignSubmittedBy;
        this.createdAt = requireNotNull(createdAt, "创建时间").truncatedTo(ChronoUnit.MILLIS);
        this.updatedAt = requireNotNull(updatedAt, "更新时间").truncatedTo(ChronoUnit.MILLIS);
    }

    public static RequirementDelivery create(InitiativeId initiativeId,
                                             ProjectId projectId,
                                             RequirementId requirementId,
                                             UserId createdBy) {
        return create(initiativeId, projectId, requirementId, createdBy,
                DeliveryStage.REQUIREMENT_REVIEW);
    }

    public static RequirementDelivery create(InitiativeId initiativeId,
                                             ProjectId projectId,
                                             RequirementId requirementId,
                                             UserId createdBy,
                                             DeliveryStage initialStage) {
        Instant now = now();
        return new RequirementDelivery(null, initiativeId, projectId, requirementId, createdBy,
                requireNotNull(initialStage, "交付初始阶段"), DeliveryStatus.ACTIVE,
                null, null, null, null, null, null, now, now);
    }

    public static RequirementDelivery restore(RequirementDeliveryId id,
                                              InitiativeId initiativeId,
                                              ProjectId projectId,
                                              RequirementId requirementId,
                                              UserId createdBy,
                                              DeliveryStage stage,
                                              DeliveryStatus status,
                                              String designSummary,
                                              String designContent,
                                              String outlineDesignSummary,
                                              String outlineDesignContent,
                                              Instant outlineDesignSubmittedAt,
                                              UserId outlineDesignSubmittedBy,
                                              Instant createdAt,
                                              Instant updatedAt) {
        return new RequirementDelivery(id, initiativeId, projectId, requirementId, createdBy,
                stage, status, designSummary, designContent,
                outlineDesignSummary, outlineDesignContent,
                outlineDesignSubmittedAt, outlineDesignSubmittedBy,
                createdAt, updatedAt);
    }

    public void approveRequirementReview() {
        requireStage(DeliveryStage.REQUIREMENT_REVIEW, "只有需求评审中的交付可以进入设计");
        stage = DeliveryStage.OUTLINE_DESIGN;
        touch();
    }

    public void submitOutlineDesign(String summary,
                                    String content,
                                    UserId submittedBy) {
        requireStage(DeliveryStage.OUTLINE_DESIGN, "只有概要设计阶段的交付可以提交概要设计");
        this.outlineDesignSummary = optionalText(summary, 4000);
        this.outlineDesignContent = optionalText(content, 60000);
        this.outlineDesignSubmittedAt = now();
        this.outlineDesignSubmittedBy = requireNotNull(submittedBy, "概要设计提交人");
        stage = DeliveryStage.DESIGN;
        touch();
    }

    public void rejectRequirementReview() {
        requireStage(DeliveryStage.REQUIREMENT_REVIEW, "只有需求评审中的交付可以记录驳回");
        touch();
    }

    public void submitDesign(String summary, String content) {
        requireStage(DeliveryStage.DESIGN, "只有设计阶段的交付可以提交设计评审");
        this.designSummary = requireText(summary, "设计摘要", 4000);
        this.designContent = requireText(content, "详细设计", 60000);
        stage = DeliveryStage.DESIGN_REVIEW;
        touch();
    }

    public void submitModularDesign(String summary, String content) {
        requireStage(DeliveryStage.DESIGN, "只有设计阶段的交付可以提交设计评审");
        this.designSummary = optionalText(summary, 4000);
        this.designContent = optionalText(content, 60000);
        stage = DeliveryStage.DESIGN_REVIEW;
        touch();
    }

    public void approveDesign() {
        requireStage(DeliveryStage.DESIGN_REVIEW, "只有设计评审中的交付可以进入研发");
        stage = DeliveryStage.DEVELOPMENT;
        touch();
    }

    public void rejectDesign() {
        requireStage(DeliveryStage.DESIGN_REVIEW, "只有设计评审中的交付可以退回设计");
        stage = DeliveryStage.DESIGN;
        touch();
    }

    public void completeDevelopment() {
        requireStage(DeliveryStage.DEVELOPMENT, "只有研发中的交付可以完成研发");
        stage = DeliveryStage.SMOKE;
        touch();
    }

    public void passSmoke() {
        requireStage(DeliveryStage.SMOKE, "只有冒烟阶段的交付可以通过冒烟");
        stage = DeliveryStage.AUTOMATION;
        touch();
    }

    public void passAutomation() {
        requireStage(DeliveryStage.AUTOMATION, "只有自动化阶段的交付可以通过自动化回归");
        stage = DeliveryStage.READY_FOR_TRANSFER;
        touch();
    }

    public void markInRelease() {
        requireStage(DeliveryStage.READY_FOR_TRANSFER, "只有待转测的交付可以纳入发布");
        stage = DeliveryStage.IN_RELEASE;
        touch();
    }

    public void markReleased() {
        requireStage(DeliveryStage.IN_RELEASE, "只有发布中的交付可以标记为已发布");
        stage = DeliveryStage.RELEASED;
        touch();
    }

    public void close() {
        if (stage != DeliveryStage.RELEASED && stage != DeliveryStage.CLOSED) {
            throw new DomainException("只有已发布的交付可以关闭，当前阶段：" + stage);
        }
        stage = DeliveryStage.CLOSED;
        status = DeliveryStatus.COMPLETED;
        touch();
    }

    public void cancel() {
        if (stage == DeliveryStage.CLOSED) {
            throw new DomainException("已关闭的交付不能取消");
        }
        if (stage == DeliveryStage.IN_RELEASE || stage == DeliveryStage.RELEASED) {
            throw new DomainException("已纳入发布的交付不能取消");
        }
        status = DeliveryStatus.CANCELLED;
        touch();
    }

    public void assignIdentity(RequirementDeliveryId assigned) {
        if (id != null) throw new DomainException("需求交付已有 ID，不能重复分配");
        id = requireNotNull(assigned, "需求交付 ID");
    }

    public boolean readyForRelease() {
        return status == DeliveryStatus.ACTIVE && stage == DeliveryStage.READY_FOR_TRANSFER;
    }

    private void requireStage(DeliveryStage expected, String message) {
        if (status != DeliveryStatus.ACTIVE || stage != expected) {
            throw new DomainException(message + "，当前阶段：" + stage + "，状态：" + status);
        }
    }

    private void touch() {
        updatedAt = now();
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    private static String requireText(String value, String field, int max) {
        if (value == null || value.isBlank()) throw new DomainException(field + "不能为空");
        String text = value.trim();
        if (text.length() > max) throw new DomainException(field + "不能超过 " + max + " 个字符");
        return text;
    }

    private static String optionalText(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String text = value.trim();
        if (text.length() > max) throw new DomainException("文本不能超过 " + max + " 个字符");
        return text;
    }

    private static <T> T requireNotNull(T value, String field) {
        if (value == null) throw new DomainException(field + "不能为空");
        return value;
    }

    public RequirementDeliveryId getId() {
        return id;
    }

    public InitiativeId getInitiativeId() {
        return initiativeId;
    }

    public ProjectId getProjectId() {
        return projectId;
    }

    public RequirementId getRequirementId() {
        return requirementId;
    }

    public UserId getCreatedBy() {
        return createdBy;
    }

    public DeliveryStage getStage() {
        return stage;
    }

    public DeliveryStatus getStatus() {
        return status;
    }

    public String getDesignSummary() {
        return designSummary;
    }

    public String getDesignContent() {
        return designContent;
    }

    public String getOutlineDesignSummary() {
        return outlineDesignSummary;
    }

    public String getOutlineDesignContent() {
        return outlineDesignContent;
    }

    public Instant getOutlineDesignSubmittedAt() {
        return outlineDesignSubmittedAt;
    }

    public UserId getOutlineDesignSubmittedBy() {
        return outlineDesignSubmittedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
