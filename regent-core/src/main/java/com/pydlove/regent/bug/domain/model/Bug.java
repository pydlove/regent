package com.pydlove.regent.bug.domain.model;

import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.requirement.domain.model.RequirementId;
import com.pydlove.regent.shared.domain.DomainException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * 缺陷单聚合。
 *
 * <p>人工录入与执行器发现共用同一条落库路径，但来源与可追溯性不同：人工单直接绑定项目，
 * AI 单还必须绑定发现它的任务。后者不是展示字段，而是复盘时必须回答「哪个执行轮次把问题带进来的」。
 */
public class Bug {

    private static final int TITLE_MAX = 256;
    private static final int FILE_PATH_MAX = 512;
    private static final int WONT_FIX_REASON_MIN = 6;

    private BugId id;
    private final ProjectId projectId;
    private final RequirementId requirementId;
    private final TaskId sourceTaskId;
    private TaskId fixTaskId;
    private final BugSource source;
    private final String title;
    private final BugSeverity severity;
    private BugStatus status;
    private final String reproduction;
    private final String filePath;
    private final String detail;
    private final Instant foundAt;
    private Instant fixedAt;
    private String wontFixReason;
    private final Instant createdAt;
    private Instant updatedAt;

    private Bug(ProjectId projectId,
                RequirementId requirementId,
                TaskId sourceTaskId,
                TaskId fixTaskId,
                BugSource source,
                String title,
                BugSeverity severity,
                BugStatus status,
                String reproduction,
                String filePath,
                String detail,
                Instant foundAt,
                Instant fixedAt,
                String wontFixReason,
                Instant createdAt,
                Instant updatedAt) {
        this.projectId = requireNotNull(projectId, "所属项目");
        this.requirementId = requirementId;
        this.sourceTaskId = sourceTaskId;
        this.fixTaskId = fixTaskId;
        this.source = requireNotNull(source, "缺陷来源");
        if (source == BugSource.MANUAL && sourceTaskId != null) {
            throw new DomainException("人工录入的缺陷不能绑定来源任务");
        }
        if (source == BugSource.AI_DISCOVERED && sourceTaskId == null) {
            throw new DomainException("AI 发现的缺陷必须绑定来源任务");
        }
        this.title = requireTitle(title);
        this.severity = requireNotNull(severity, "缺陷严重级");
        this.status = requireNotNull(status, "缺陷状态");
        this.reproduction = blankToNull(reproduction);
        this.filePath = requireFilePath(filePath);
        this.detail = blankToNull(detail);
        this.foundAt = requireNotNull(foundAt, "发现时间");
        this.fixedAt = fixedAt;
        this.wontFixReason = blankToNull(wontFixReason);
        this.createdAt = requireNotNull(createdAt, "创建时间");
        this.updatedAt = requireNotNull(updatedAt, "更新时间");
    }

    /** 人工录入缺陷。 */
    public static Bug reportManually(ProjectId projectId,
                                     String title,
                                     BugSeverity severity,
                                     String reproduction,
                                     String filePath,
                                     String detail) {
        Instant now = now();
        return new Bug(projectId, null, null, null, BugSource.MANUAL, title, severity,
                BugStatus.OPEN, reproduction, filePath, detail, now, null, null, now, now);
    }

    /** 执行器在一轮执行中发现的缺陷。 */
    public static Bug discoveredByAi(ProjectId projectId,
                                     RequirementId requirementId,
                                     TaskId sourceTaskId,
                                     String title,
                                     BugSeverity severity,
                                     String reproduction,
                                     String filePath,
                                     String detail) {
        Instant now = now();
        return new Bug(projectId, requirementId, sourceTaskId, null, BugSource.AI_DISCOVERED,
                title, severity, BugStatus.OPEN, reproduction, filePath, detail,
                now, null, null, now, now);
    }

    /** 从存储重建，仅允许基础设施层调用。 */
    public static Bug restore(BugSnapshot snapshot) {
        Bug bug = new Bug(
                snapshot.projectId(), snapshot.requirementId(), snapshot.sourceTaskId(),
                snapshot.fixTaskId(), snapshot.source(), snapshot.title(), snapshot.severity(),
                snapshot.status(), snapshot.reproduction(), snapshot.filePath(), snapshot.detail(),
                snapshot.foundAt(), snapshot.fixedAt(), snapshot.wontFixReason(),
                snapshot.createdAt(), snapshot.updatedAt());
        bug.id = requireNotNull(snapshot.id(), "缺陷单 ID");
        return bug;
    }

    public void assignIdentity(BugId assigned) {
        if (id != null) {
            throw new DomainException("缺陷单已有 ID，不能重复分配：" + id);
        }
        id = requireNotNull(assigned, "缺陷单 ID");
    }

    /** 派发修复任务：缺陷从待处理进入修复中，并只保存另一个聚合的 ID。 */
    public void requestFix(TaskId fixTaskId) {
        if (status != BugStatus.OPEN) {
            throw new DomainException("只有待处理缺陷可以派发修复任务，当前状态：" + status);
        }
        if (this.fixTaskId != null) {
            throw new DomainException("缺陷单已经派发过修复任务：" + this.fixTaskId);
        }
        this.fixTaskId = requireNotNull(fixTaskId, "修复任务");
        this.status = BugStatus.FIXING;
        touch();
    }

    /** 修复任务成功只代表代码已改完，缺陷仍需人工验证。 */
    public void markFixed() {
        requireStatus(BugStatus.FIXING, "只有修复中的缺陷可以标记为已修复");
        this.status = BugStatus.FIXED;
        this.fixedAt = now();
        touch();
    }

    /** 人工验证通过后关闭缺陷。 */
    public void verify() {
        requireStatus(BugStatus.FIXED, "只有待验证的缺陷可以验证关闭");
        this.status = BugStatus.CLOSED;
        touch();
    }

    /** 尚未解决的缺陷都可以判定不修复；终态不能再改，避免覆盖已经留下的结论。 */
    public void wontFix(String reason) {
        if (status.isResolved()) {
            throw new DomainException("缺陷已处于终态，不能标记为不修复：" + status);
        }
        this.wontFixReason = requireWontFixReason(reason);
        this.status = BugStatus.WONTFIX;
        touch();
    }

    private static String requireTitle(String title) {
        String value = requireText(title, "缺陷标题");
        if (value.length() > TITLE_MAX) {
            throw new DomainException("缺陷标题超长（上限 " + TITLE_MAX + " 字符）：" + value.length());
        }
        return value;
    }

    private static String requireFilePath(String filePath) {
        String value = blankToNull(filePath);
        if (value != null && value.length() > FILE_PATH_MAX) {
            throw new DomainException("缺陷文件路径超长（上限 " + FILE_PATH_MAX + " 字符）：" + value.length());
        }
        return value;
    }

    private static String requireWontFixReason(String reason) {
        String value = requireText(reason, "不修复理由");
        if (value.length() < WONT_FIX_REASON_MIN) {
            throw new DomainException("不修复理由至少需要 " + WONT_FIX_REASON_MIN + " 个字符");
        }
        return value;
    }

    private static <T> T requireNotNull(T value, String field) {
        if (value == null) {
            throw new DomainException(field + "不能为空");
        }
        return value;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new DomainException(field + "不能为空");
        }
        return value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    private void requireStatus(BugStatus expected, String message) {
        if (status != expected) {
            throw new DomainException(message + "，当前状态：" + status);
        }
    }

    private void touch() {
        this.updatedAt = now();
    }

    public BugId getId() {
        return id;
    }

    public ProjectId getProjectId() {
        return projectId;
    }

    public Optional<RequirementId> getRequirementId() {
        return Optional.ofNullable(requirementId);
    }

    public Optional<TaskId> getSourceTaskId() {
        return Optional.ofNullable(sourceTaskId);
    }

    public Optional<TaskId> getFixTaskId() {
        return Optional.ofNullable(fixTaskId);
    }

    public BugSource getSource() {
        return source;
    }

    public String getTitle() {
        return title;
    }

    public BugSeverity getSeverity() {
        return severity;
    }

    public BugStatus getStatus() {
        return status;
    }

    public Optional<String> getReproduction() {
        return Optional.ofNullable(reproduction);
    }

    public Optional<String> getFilePath() {
        return Optional.ofNullable(filePath);
    }

    public Optional<String> getDetail() {
        return Optional.ofNullable(detail);
    }

    public Instant getFoundAt() {
        return foundAt;
    }

    public Optional<Instant> getFixedAt() {
        return Optional.ofNullable(fixedAt);
    }

    public Optional<String> getWontFixReason() {
        return Optional.ofNullable(wontFixReason);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
