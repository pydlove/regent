package com.pydlove.regent.delivery.domain.model;

import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.domain.DomainException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;

/** 一次发布轮次。修复后重新转测会创建新的 attempt，而不是覆盖历史。 */
public class Release {

    private ReleaseId id;
    private final ProjectId projectId;
    private final String version;
    private final int attemptNo;
    private final BuildArtifactId buildArtifactId;
    private final List<RequirementDeliveryId> deliveryIds;
    private ReleaseStage stage;
    private ReleaseStatus status;
    private final String releaseNotes;
    private final String rollbackPlan;
    private final Instant createdAt;
    private Instant updatedAt;

    private Release(ReleaseId id,
                    ProjectId projectId,
                    String version,
                    int attemptNo,
                    BuildArtifactId buildArtifactId,
                    List<RequirementDeliveryId> deliveryIds,
                    ReleaseStage stage,
                    ReleaseStatus status,
                    String releaseNotes,
                    String rollbackPlan,
                    Instant createdAt,
                    Instant updatedAt) {
        this.id = id;
        this.projectId = requireNotNull(projectId, "项目 ID");
        this.version = requireText(version, "版本", 64);
        if (attemptNo <= 0) throw new DomainException("发布轮次必须为正数");
        this.attemptNo = attemptNo;
        this.buildArtifactId = requireNotNull(buildArtifactId, "构建制品");
        if (deliveryIds == null || deliveryIds.isEmpty()) {
            throw new DomainException("发布至少需要一个需求交付单");
        }
        this.deliveryIds = List.copyOf(new LinkedHashSet<>(deliveryIds));
        this.stage = requireNotNull(stage, "发布阶段");
        this.status = requireNotNull(status, "发布状态");
        this.releaseNotes = optionalText(releaseNotes, 8000);
        this.rollbackPlan = requireText(rollbackPlan, "回滚方案", 8000);
        this.createdAt = requireNotNull(createdAt, "创建时间").truncatedTo(ChronoUnit.MILLIS);
        this.updatedAt = requireNotNull(updatedAt, "更新时间").truncatedTo(ChronoUnit.MILLIS);
    }

    public static Release create(ProjectId projectId,
                                 String version,
                                 int attemptNo,
                                 BuildArtifactId buildArtifactId,
                                 List<RequirementDeliveryId> deliveryIds,
                                 String releaseNotes,
                                 String rollbackPlan) {
        Instant now = now();
        return new Release(null, projectId, version, attemptNo, buildArtifactId, deliveryIds,
                ReleaseStage.TRANSFER, ReleaseStatus.ACTIVE,
                releaseNotes, rollbackPlan, now, now);
    }

    public static Release restore(ReleaseId id,
                                  ProjectId projectId,
                                  String version,
                                  int attemptNo,
                                  BuildArtifactId buildArtifactId,
                                  List<RequirementDeliveryId> deliveryIds,
                                  ReleaseStage stage,
                                  ReleaseStatus status,
                                  String releaseNotes,
                                  String rollbackPlan,
                                  Instant createdAt,
                                  Instant updatedAt) {
        return new Release(id, projectId, version, attemptNo, buildArtifactId, deliveryIds,
                stage, status, releaseNotes, rollbackPlan, createdAt, updatedAt);
    }

    public void approveTransfer() {
        requireStage(ReleaseStage.TRANSFER, "只有待转测的发布可以批准转测");
        stage = ReleaseStage.INTEGRATION;
        touch();
    }

    public void passIntegration() {
        requireStage(ReleaseStage.INTEGRATION, "只有集成测试中的发布可以通过集成测试");
        stage = ReleaseStage.REGRESSION;
        touch();
    }

    public void passRegression() {
        requireStage(ReleaseStage.REGRESSION, "只有回归测试中的发布可以通过版本回归");
        stage = ReleaseStage.RELEASE_REVIEW;
        touch();
    }

    public void approveRelease() {
        requireStage(ReleaseStage.RELEASE_REVIEW, "只有发版评审中的发布可以批准发版");
        stage = ReleaseStage.PRODUCTION;
        touch();
    }

    public void markDeployed() {
        requireStage(ReleaseStage.PRODUCTION, "只有待生产发布的发布可以确认部署完成");
        stage = ReleaseStage.OBSERVING;
        touch();
    }

    public void close() {
        if (status == ReleaseStatus.RELEASED) return;
        requireStage(ReleaseStage.OBSERVING, "只有观察中的发布可以关闭");
        stage = ReleaseStage.CLOSED;
        status = ReleaseStatus.RELEASED;
        touch();
    }

    public void cancel() {
        if (status != ReleaseStatus.ACTIVE) {
            throw new DomainException("只有进行中的发布可以取消");
        }
        status = ReleaseStatus.CANCELLED;
        touch();
    }

    public void supersede() {
        if (status != ReleaseStatus.ACTIVE) return;
        status = ReleaseStatus.SUPERSEDED;
        touch();
    }

    public void assignIdentity(ReleaseId assigned) {
        if (id != null) throw new DomainException("发布已有 ID，不能重复分配");
        id = requireNotNull(assigned, "发布 ID");
    }

    private void requireStage(ReleaseStage expected, String message) {
        if (status != ReleaseStatus.ACTIVE || stage != expected) {
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

    public ReleaseId getId() {
        return id;
    }

    public ProjectId getProjectId() {
        return projectId;
    }

    public String getVersion() {
        return version;
    }

    public int getAttemptNo() {
        return attemptNo;
    }

    public BuildArtifactId getBuildArtifactId() {
        return buildArtifactId;
    }

    public List<RequirementDeliveryId> getDeliveryIds() {
        return deliveryIds;
    }

    public ReleaseStage getStage() {
        return stage;
    }

    public ReleaseStatus getStatus() {
        return status;
    }

    public String getReleaseNotes() {
        return releaseNotes;
    }

    public String getRollbackPlan() {
        return rollbackPlan;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
