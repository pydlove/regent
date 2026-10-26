package com.pydlove.regent.review.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

import java.time.Instant;

/**
 * AI 或规则提出的具体审阅发现。
 *
 * <p>阻断级发现必须能被定位到文件和行号。没有代码位置的判断可以成为提示，但不能伪装成
 * 阻断问题参与验收。
 */
public class ReviewFinding {

    private ReviewFindingId id;
    private final ReviewChangeUnitId changeUnitId;
    private final ReviewFindingSeverity severity;
    private final String title;
    private final String description;
    private final String impact;
    private final String filePath;
    private final Integer lineStart;
    private final Integer lineEnd;
    private final String suggestedAction;
    private final ReviewEvidenceId evidenceId;
    private ReviewFindingStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    private ReviewFinding(ReviewFindingId id,
                          ReviewChangeUnitId changeUnitId,
                          ReviewFindingSeverity severity,
                          ReviewFindingStatus status,
                          String title,
                          String description,
                          String impact,
                          String filePath,
                          Integer lineStart,
                          Integer lineEnd,
                          String suggestedAction,
                          ReviewEvidenceId evidenceId,
                          Instant createdAt,
                          Instant updatedAt) {
        this.id = id;
        this.changeUnitId = changeUnitId;
        this.severity = requireNotNull(severity, "发现严重级别");
        this.status = requireNotNull(status, "发现状态");
        this.title = requireText(title, "发现标题");
        this.description = requireText(description, "发现描述");
        this.impact = blankToNull(impact);
        this.filePath = normalizePath(filePath);
        this.lineStart = positiveOrNull(lineStart, "发现起始行号");
        this.lineEnd = positiveOrNull(lineEnd, "发现结束行号");
        if (this.lineStart != null && this.lineEnd != null && this.lineEnd < this.lineStart) {
            throw new DomainException("发现结束行号不能小于起始行号");
        }
        if (severity == ReviewFindingSeverity.BLOCKER
                && (this.filePath == null || this.lineStart == null)) {
            throw new DomainException("阻断级发现必须带文件与行号");
        }
        this.suggestedAction = blankToNull(suggestedAction);
        this.evidenceId = evidenceId;
        this.createdAt = requireNotNull(createdAt, "发现创建时间");
        this.updatedAt = requireNotNull(updatedAt, "发现更新时间");
    }

    public static ReviewFinding create(ReviewChangeUnitId changeUnitId,
                                       ReviewFindingSeverity severity,
                                       String title,
                                       String description,
                                       String impact,
                                       String filePath,
                                       Integer lineStart,
                                       Integer lineEnd,
                                       String suggestedAction,
                                       ReviewEvidenceId evidenceId,
                                       Instant now) {
        return new ReviewFinding(null, changeUnitId, severity, ReviewFindingStatus.PENDING,
                title, description, impact, filePath, lineStart, lineEnd,
                suggestedAction, evidenceId, now, now);
    }

    public static ReviewFinding restore(ReviewFindingId id,
                                        ReviewChangeUnitId changeUnitId,
                                        ReviewFindingSeverity severity,
                                        ReviewFindingStatus status,
                                        String title,
                                        String description,
                                        String impact,
                                        String filePath,
                                        Integer lineStart,
                                        Integer lineEnd,
                                        String suggestedAction,
                                        ReviewEvidenceId evidenceId,
                                        Instant createdAt,
                                        Instant updatedAt) {
        return new ReviewFinding(requireNotNull(id, "发现 ID"), changeUnitId, severity, status,
                title, description, impact, filePath, lineStart, lineEnd,
                suggestedAction, evidenceId, createdAt, updatedAt);
    }

    public void markStatus(ReviewFindingStatus next, Instant at) {
        if (next == null) {
            throw new DomainException("发现状态不能为空");
        }
        if (status == next) {
            return;
        }
        status = next;
        updatedAt = requireNotNull(at, "发现更新时间");
    }

    public void assignIdentity(ReviewFindingId assigned) {
        if (id != null) {
            throw new DomainException("发现已有 ID，不能重复分配：" + id);
        }
        id = requireNotNull(assigned, "发现 ID");
    }

    public boolean isUnresolvedBlocker() {
        return severity == ReviewFindingSeverity.BLOCKER && !status.isResolved();
    }

    public ReviewFindingId getId() {
        return id;
    }

    public ReviewChangeUnitId getChangeUnitId() {
        return changeUnitId;
    }

    public ReviewFindingSeverity getSeverity() {
        return severity;
    }

    public ReviewFindingStatus getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getImpact() {
        return impact;
    }

    public String getFilePath() {
        return filePath;
    }

    public Integer getLineStart() {
        return lineStart;
    }

    public Integer getLineEnd() {
        return lineEnd;
    }

    public String getSuggestedAction() {
        return suggestedAction;
    }

    public ReviewEvidenceId getEvidenceId() {
        return evidenceId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    private static String normalizePath(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String path = value.replace('\\', '/').strip();
        if (path.startsWith("/") || path.contains("../")) {
            throw new DomainException("发现文件必须位于项目内：" + path);
        }
        return path;
    }

    private static Integer positiveOrNull(Integer value, String field) {
        if (value == null) {
            return null;
        }
        if (value < 1) {
            throw new DomainException(field + "必须为正数");
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
        return value.strip();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
