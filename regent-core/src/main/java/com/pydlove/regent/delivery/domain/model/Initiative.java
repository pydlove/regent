package com.pydlove.regent.delivery.domain.model;

import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.domain.DomainException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** 立项单：先确认业务价值和投入，再允许创建需求交付。 */
public class Initiative {

    private InitiativeId id;
    private final ProjectId projectId;
    private final ProductVersionId versionId;
    private final UserId createdBy;
    private String title;
    private String businessGoal;
    private String scopeText;
    private String outOfScopeText;
    private String successMetrics;
    private String stakeholders;
    private String targetVersion;
    private String riskText;
    private String meetingMinutes;
    private InitiativeStatus status;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant submittedAt;
    private Instant approvedAt;
    private Instant rejectedAt;

    private Initiative(InitiativeId id,
                       ProjectId projectId,
                       ProductVersionId versionId,
                       UserId createdBy,
                       String title,
                       String businessGoal,
                       String scopeText,
                       String outOfScopeText,
                       String successMetrics,
                       String stakeholders,
                       String targetVersion,
                       String riskText,
                       String meetingMinutes,
                       InitiativeStatus status,
                       Instant createdAt,
                       Instant updatedAt,
                       Instant submittedAt,
                       Instant approvedAt,
                       Instant rejectedAt) {
        this.id = id;
        this.projectId = requireNotNull(projectId, "所属项目");
        this.versionId = versionId;
        this.createdBy = requireNotNull(createdBy, "创建人");
        this.title = requireText(title, "立项名称", 160);
        this.businessGoal = requireText(businessGoal, "业务目标", 4000);
        this.scopeText = optionalText(scopeText, 8000);
        this.outOfScopeText = optionalText(outOfScopeText, 8000);
        this.successMetrics = optionalText(successMetrics, 4000);
        this.stakeholders = optionalText(stakeholders, 4000);
        this.targetVersion = optionalText(targetVersion, 64);
        this.riskText = optionalText(riskText, 8000);
        this.meetingMinutes = optionalText(meetingMinutes, 60000);
        this.status = requireNotNull(status, "立项状态");
        this.createdAt = requireNotNull(createdAt, "创建时间").truncatedTo(ChronoUnit.MILLIS);
        this.updatedAt = requireNotNull(updatedAt, "更新时间").truncatedTo(ChronoUnit.MILLIS);
        this.submittedAt = submittedAt;
        this.approvedAt = approvedAt;
        this.rejectedAt = rejectedAt;
    }

    public static Initiative create(ProjectId projectId,
                                    UserId createdBy,
                                    String title,
                                    String businessGoal,
                                    String scopeText,
                                    String outOfScopeText,
                                    String successMetrics) {
        Instant now = now();
        return new Initiative(null, projectId, null, createdBy, title, businessGoal, scopeText,
                outOfScopeText, successMetrics, null, null, null, null,
                InitiativeStatus.DRAFT, now, now, null, null, null);
    }

    public static Initiative create(ProjectId projectId,
                                    UserId createdBy,
                                    String title,
                                    String businessGoal,
                                    String scopeText,
                                    String outOfScopeText,
                                    String successMetrics,
                                    String stakeholders,
                                    String targetVersion,
                                    String riskText) {
        Instant now = now();
        return new Initiative(null, projectId, null, createdBy, title, businessGoal, scopeText,
                outOfScopeText, successMetrics, stakeholders, targetVersion, riskText, null,
                InitiativeStatus.DRAFT, now, now, null, null, null);
    }

    public static Initiative create(ProjectId projectId,
                                    ProductVersionId versionId,
                                    UserId createdBy,
                                    String title,
                                    String businessGoal,
                                    String scopeText,
                                    String outOfScopeText,
                                    String successMetrics,
                                    String stakeholders,
                                    String targetVersion,
                                    String riskText) {
        Instant now = now();
        return new Initiative(null, projectId, requireNotNull(versionId, "产品版本"),
                createdBy, title, businessGoal, scopeText,
                outOfScopeText, successMetrics, stakeholders, targetVersion, riskText, null,
                InitiativeStatus.DRAFT, now, now, null, null, null);
    }

    public static Initiative restore(InitiativeId id,
                                     ProjectId projectId,
                                     UserId createdBy,
                                     String title,
                                     String businessGoal,
                                     String scopeText,
                                     String outOfScopeText,
                                     String successMetrics,
                                     InitiativeStatus status,
                                     Instant createdAt,
                                     Instant updatedAt,
                                     Instant submittedAt,
                                     Instant approvedAt,
                                     Instant rejectedAt) {
        return new Initiative(id, projectId, null, createdBy, title, businessGoal, scopeText,
                outOfScopeText, successMetrics, null, null, null, null,
                status, createdAt, updatedAt,
                submittedAt, approvedAt, rejectedAt);
    }

    public static Initiative restore(InitiativeId id,
                                     ProjectId projectId,
                                     UserId createdBy,
                                     String title,
                                     String businessGoal,
                                     String scopeText,
                                     String outOfScopeText,
                                     String successMetrics,
                                     String stakeholders,
                                     String targetVersion,
                                     String riskText,
                                     InitiativeStatus status,
                                     Instant createdAt,
                                     Instant updatedAt,
                                     Instant submittedAt,
                                     Instant approvedAt,
                                     Instant rejectedAt) {
        return new Initiative(id, projectId, null, createdBy, title, businessGoal, scopeText,
                outOfScopeText, successMetrics, stakeholders, targetVersion, riskText, null,
                status, createdAt, updatedAt, submittedAt, approvedAt, rejectedAt);
    }

    public static Initiative restore(InitiativeId id,
                                     ProjectId projectId,
                                     ProductVersionId versionId,
                                     UserId createdBy,
                                     String title,
                                     String businessGoal,
                                     String scopeText,
                                     String outOfScopeText,
                                     String successMetrics,
                                     String stakeholders,
                                     String targetVersion,
                                     String riskText,
                                     String meetingMinutes,
                                     InitiativeStatus status,
                                     Instant createdAt,
                                     Instant updatedAt,
                                     Instant submittedAt,
                                     Instant approvedAt,
                                     Instant rejectedAt) {
        return new Initiative(id, projectId, versionId, createdBy, title, businessGoal, scopeText,
                outOfScopeText, successMetrics, stakeholders, targetVersion, riskText,
                meetingMinutes, status, createdAt, updatedAt,
                submittedAt, approvedAt, rejectedAt);
    }

    public void update(String title,
                       String businessGoal,
                       String scopeText,
                       String outOfScopeText,
                       String successMetrics) {
        if (status != InitiativeStatus.DRAFT && status != InitiativeStatus.REJECTED) {
            throw new DomainException("只有草稿或已驳回的立项可以修改");
        }
        this.title = requireText(title, "立项名称", 160);
        this.businessGoal = requireText(businessGoal, "业务目标", 4000);
        this.scopeText = optionalText(scopeText, 8000);
        this.outOfScopeText = optionalText(outOfScopeText, 8000);
        this.successMetrics = optionalText(successMetrics, 4000);
        touch();
    }

    public void updateMeetingMinutes(String meetingMinutes) {
        if (status != InitiativeStatus.DRAFT && status != InitiativeStatus.REJECTED) {
            throw new DomainException("只有草稿或已驳回的立项可以修改会议纪要");
        }
        this.meetingMinutes = optionalText(meetingMinutes, 60000);
        touch();
    }

    public void update(String title,
                       String businessGoal,
                       String scopeText,
                       String outOfScopeText,
                       String successMetrics,
                       String stakeholders,
                       String targetVersion,
                       String riskText) {
        update(title, businessGoal, scopeText, outOfScopeText, successMetrics);
        this.stakeholders = optionalText(stakeholders, 4000);
        this.targetVersion = optionalText(targetVersion, 64);
        this.riskText = optionalText(riskText, 8000);
        touch();
    }

    public void submit() {
        if (status != InitiativeStatus.DRAFT && status != InitiativeStatus.REJECTED) {
            throw new DomainException("只有草稿或已驳回的立项可以提交审批");
        }
        status = InitiativeStatus.SUBMITTED;
        submittedAt = now();
        rejectedAt = null;
        touch();
    }

    public void approve() {
        approve(null);
    }

    public void approve(String meetingMinutes) {
        if (status != InitiativeStatus.SUBMITTED) {
            throw new DomainException("只有已提交的立项可以批准");
        }
        if (meetingMinutes != null && !meetingMinutes.isBlank()) {
            this.meetingMinutes = optionalText(meetingMinutes, 60000);
        }
        status = InitiativeStatus.APPROVED;
        approvedAt = now();
        touch();
    }

    public void reject() {
        reject(null);
    }

    public void reject(String meetingMinutes) {
        if (status != InitiativeStatus.SUBMITTED) {
            throw new DomainException("只有已提交的立项可以驳回");
        }
        if (meetingMinutes != null && !meetingMinutes.isBlank()) {
            this.meetingMinutes = optionalText(meetingMinutes, 60000);
        }
        status = InitiativeStatus.REJECTED;
        rejectedAt = now();
        touch();
    }

    public void requireEvidenceEditable() {
        if (status == InitiativeStatus.APPROVED || status == InitiativeStatus.CANCELLED) {
            throw new DomainException("立项已批准或已取消，不能修改审批材料");
        }
    }

    public void cancel() {
        if (status == InitiativeStatus.CANCELLED) return;
        status = InitiativeStatus.CANCELLED;
        touch();
    }

    public void assignIdentity(InitiativeId assigned) {
        if (id != null) throw new DomainException("立项已有 ID，不能重复分配");
        id = requireNotNull(assigned, "立项 ID");
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

    public InitiativeId getId() {
        return id;
    }

    public ProjectId getProjectId() {
        return projectId;
    }

    public ProductVersionId getVersionId() {
        return versionId;
    }

    public UserId getCreatedBy() {
        return createdBy;
    }

    public String getTitle() {
        return title;
    }

    public String getBusinessGoal() {
        return businessGoal;
    }

    public String getScopeText() {
        return scopeText;
    }

    public String getOutOfScopeText() {
        return outOfScopeText;
    }

    public String getSuccessMetrics() {
        return successMetrics;
    }

    public String getStakeholders() {
        return stakeholders;
    }

    public String getTargetVersion() {
        return targetVersion;
    }

    public String getRiskText() {
        return riskText;
    }

    public String getMeetingMinutes() {
        return meetingMinutes;
    }

    public InitiativeStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public Instant getRejectedAt() {
        return rejectedAt;
    }
}
