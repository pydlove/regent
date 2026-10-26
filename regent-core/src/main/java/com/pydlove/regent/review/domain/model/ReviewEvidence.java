package com.pydlove.regent.review.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

import java.time.Instant;

/** 可追溯到测试、运行记录或设备事实的验证证据。 */
public class ReviewEvidence {

    private ReviewEvidenceId id;
    private final ReviewChangeUnitId changeUnitId;
    private final ReviewEvidenceType type;
    private ReviewEvidenceStatus status;
    private final String title;
    private final String command;
    private String summary;
    private final String sourceType;
    private final Long sourceId;
    private Instant occurredAt;

    private ReviewEvidence(ReviewEvidenceId id,
                           ReviewChangeUnitId changeUnitId,
                           ReviewEvidenceType type,
                           ReviewEvidenceStatus status,
                           String title,
                           String command,
                           String summary,
                           String sourceType,
                           Long sourceId,
                           Instant occurredAt) {
        this.id = id;
        this.changeUnitId = changeUnitId;
        this.type = requireNotNull(type, "证据类型");
        this.status = requireNotNull(status, "证据状态");
        this.title = requireText(title, "证据标题");
        this.command = blankToNull(command);
        this.summary = blankToNull(summary);
        this.sourceType = requireText(sourceType, "证据来源类型");
        this.sourceId = sourceId;
        this.occurredAt = requireNotNull(occurredAt, "证据发生时间");
    }

    public static ReviewEvidence create(ReviewChangeUnitId changeUnitId,
                                        ReviewEvidenceType type,
                                        ReviewEvidenceStatus status,
                                        String title,
                                        String command,
                                        String summary,
                                        String sourceType,
                                        Long sourceId,
                                        Instant occurredAt) {
        return new ReviewEvidence(null, changeUnitId, type, status, title, command, summary,
                sourceType, sourceId, occurredAt);
    }

    public static ReviewEvidence restore(ReviewEvidenceId id,
                                         ReviewChangeUnitId changeUnitId,
                                         ReviewEvidenceType type,
                                         ReviewEvidenceStatus status,
                                         String title,
                                         String command,
                                         String summary,
                                         String sourceType,
                                         Long sourceId,
                                         Instant occurredAt) {
        return new ReviewEvidence(requireNotNull(id, "证据 ID"), changeUnitId, type, status,
                title, command, summary, sourceType, sourceId, occurredAt);
    }

    public void assignIdentity(ReviewEvidenceId assigned) {
        if (id != null) {
            throw new DomainException("证据已有 ID，不能重复分配：" + id);
        }
        id = requireNotNull(assigned, "证据 ID");
    }

    /** 测试执行会晚于任务收场结束；开放审阅期间允许按同一来源刷新状态与摘要。 */
    public boolean refresh(ReviewEvidenceStatus nextStatus, String nextSummary, Instant at) {
        ReviewEvidenceStatus normalizedStatus = requireNotNull(nextStatus, "证据状态");
        String normalizedSummary = blankToNull(nextSummary);
        Instant normalizedAt = requireNotNull(at, "证据发生时间");
        boolean changed = status != normalizedStatus
                || !java.util.Objects.equals(summary, normalizedSummary)
                || !occurredAt.equals(normalizedAt);
        status = normalizedStatus;
        summary = normalizedSummary;
        occurredAt = normalizedAt;
        return changed;
    }

    public ReviewEvidenceId getId() {
        return id;
    }

    public ReviewChangeUnitId getChangeUnitId() {
        return changeUnitId;
    }

    public ReviewEvidenceType getType() {
        return type;
    }

    public ReviewEvidenceStatus getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }

    public String getCommand() {
        return command;
    }

    public String getSummary() {
        return summary;
    }

    public String getSourceType() {
        return sourceType;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
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
        return value.strip();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
