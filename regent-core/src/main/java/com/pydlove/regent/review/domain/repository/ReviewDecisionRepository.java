package com.pydlove.regent.review.domain.repository;

import com.pydlove.regent.review.domain.model.ReviewDecision;
import com.pydlove.regent.review.domain.model.ReviewSnapshotId;

import java.util.List;
import java.util.Optional;

/** 审阅结论仓储只提供追加与读取，不提供修改。 */
public interface ReviewDecisionRepository {

    ReviewDecision save(ReviewDecision decision);

    List<ReviewDecision> findBySnapshot(ReviewSnapshotId snapshotId);

    Optional<ReviewDecision> findLatestBySnapshot(ReviewSnapshotId snapshotId);
}
