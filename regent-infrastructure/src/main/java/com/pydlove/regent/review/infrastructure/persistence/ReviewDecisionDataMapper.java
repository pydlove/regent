package com.pydlove.regent.review.infrastructure.persistence;

import com.pydlove.regent.review.domain.model.ReviewDecision;
import com.pydlove.regent.review.domain.model.ReviewDecisionId;
import com.pydlove.regent.review.domain.model.ReviewDecisionType;
import com.pydlove.regent.review.domain.model.ReviewOperatorId;
import com.pydlove.regent.review.domain.model.ReviewSnapshotId;

/** 审阅结论行与领域对象转换。 */
public final class ReviewDecisionDataMapper {

    private ReviewDecisionDataMapper() {
    }

    public static ReviewDecisionData toData(ReviewDecision decision) {
        ReviewDecisionData data = new ReviewDecisionData();
        data.setId(decision.getId() == null ? null : decision.getId().value());
        data.setSnapshotId(decision.getSnapshotId().value());
        data.setDecisionType(decision.getDecision().name());
        data.setOperatorId(decision.getOperatorId().value());
        data.setCommentText(decision.getComment());
        data.setCreatedAt(ReviewSnapshotDataMapper.toDb(decision.getCreatedAt()));
        return data;
    }

    public static ReviewDecision toDomain(ReviewDecisionData data) {
        return ReviewDecision.restore(
                ReviewDecisionId.of(data.getId()),
                ReviewSnapshotId.of(data.getSnapshotId()),
                ReviewDecisionType.valueOf(data.getDecisionType()),
                ReviewOperatorId.of(data.getOperatorId()),
                data.getCommentText(),
                ReviewSnapshotDataMapper.fromDb(data.getCreatedAt()));
    }
}
