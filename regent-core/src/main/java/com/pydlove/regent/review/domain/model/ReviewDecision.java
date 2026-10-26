package com.pydlove.regent.review.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

import java.time.Instant;

/** 只追加不修改的人工验收事实。 */
public class ReviewDecision {

    private ReviewDecisionId id;
    private final ReviewSnapshotId snapshotId;
    private final ReviewDecisionType decision;
    private final ReviewOperatorId operatorId;
    private final String comment;
    private final Instant createdAt;

    private ReviewDecision(ReviewDecisionId id,
                           ReviewSnapshotId snapshotId,
                           ReviewDecisionType decision,
                           ReviewOperatorId operatorId,
                           String comment,
                           Instant createdAt) {
        this.id = id;
        this.snapshotId = requireNotNull(snapshotId, "审阅快照");
        this.decision = requireNotNull(decision, "审阅结论");
        this.operatorId = requireNotNull(operatorId, "审阅操作者");
        this.comment = normalizeComment(decision, comment);
        this.createdAt = requireNotNull(createdAt, "审阅结论时间");
    }

    public static ReviewDecision create(ReviewSnapshotId snapshotId,
                                        ReviewDecisionType decision,
                                        ReviewOperatorId operatorId,
                                        String comment,
                                        Instant now) {
        return new ReviewDecision(null, snapshotId, decision, operatorId, comment, now);
    }

    public static ReviewDecision restore(ReviewDecisionId id,
                                         ReviewSnapshotId snapshotId,
                                         ReviewDecisionType decision,
                                         ReviewOperatorId operatorId,
                                         String comment,
                                         Instant createdAt) {
        return new ReviewDecision(requireNotNull(id, "审阅结论 ID"), snapshotId, decision,
                operatorId, comment, createdAt);
    }

    public void assignIdentity(ReviewDecisionId assigned) {
        if (id != null) {
            throw new DomainException("审阅结论已有 ID，不能重复分配：" + id);
        }
        id = requireNotNull(assigned, "审阅结论 ID");
    }

    private static String normalizeComment(ReviewDecisionType decision, String value) {
        String normalized = value == null || value.isBlank() ? null : value.strip();
        if (decision != ReviewDecisionType.APPROVED && normalized == null) {
            throw new DomainException("要求修改或驳回必须填写意见");
        }
        if (normalized != null && normalized.length() > ReviewSnapshot.MAX_COMMENT_LENGTH) {
            throw new DomainException("审阅意见超长（上限 "
                    + ReviewSnapshot.MAX_COMMENT_LENGTH + " 字符）");
        }
        return normalized;
    }

    private static <T> T requireNotNull(T value, String field) {
        if (value == null) {
            throw new DomainException(field + "不能为空");
        }
        return value;
    }

    public ReviewDecisionId getId() {
        return id;
    }

    public ReviewSnapshotId getSnapshotId() {
        return snapshotId;
    }

    public ReviewDecisionType getDecision() {
        return decision;
    }

    public ReviewOperatorId getOperatorId() {
        return operatorId;
    }

    public String getComment() {
        return comment;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
