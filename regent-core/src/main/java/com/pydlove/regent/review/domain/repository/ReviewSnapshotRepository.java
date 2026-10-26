package com.pydlove.regent.review.domain.repository;

import com.pydlove.regent.review.domain.model.ReviewSnapshot;
import com.pydlove.regent.review.domain.model.ReviewSnapshotId;
import com.pydlove.regent.review.domain.model.ReviewTaskId;
import com.pydlove.regent.review.domain.model.ReviewTaskRunId;

import java.util.List;
import java.util.Optional;

/** 审阅快照聚合仓储：完整装载快照与它的变更单元、发现和证据。 */
public interface ReviewSnapshotRepository {

    Optional<ReviewSnapshot> findById(ReviewSnapshotId id);

    Optional<ReviewSnapshot> findByTask(ReviewTaskId taskId);

    Optional<ReviewSnapshot> findCurrentByTask(ReviewTaskId taskId);

    Optional<ReviewSnapshot> findByTaskRun(ReviewTaskRunId taskRunId);

    ReviewSnapshot save(ReviewSnapshot snapshot);

    List<ReviewSnapshot> findUnexpiredByTask(ReviewTaskId taskId);
}
