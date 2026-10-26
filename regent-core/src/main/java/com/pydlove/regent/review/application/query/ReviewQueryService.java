package com.pydlove.regent.review.application.query;

import com.pydlove.regent.review.domain.model.ReviewDecision;
import com.pydlove.regent.review.domain.model.ReviewSnapshot;
import com.pydlove.regent.review.domain.model.ReviewSnapshotId;
import com.pydlove.regent.review.domain.model.ReviewTaskId;
import com.pydlove.regent.review.domain.repository.ReviewDecisionRepository;
import com.pydlove.regent.review.domain.repository.ReviewSnapshotRepository;
import com.pydlove.regent.shared.domain.EntityNotFoundException;

import java.util.List;
import java.util.Optional;

/** 审阅读侧用例。 */
public class ReviewQueryService {

    private final ReviewSnapshotRepository snapshotRepository;
    private final ReviewDecisionRepository decisionRepository;

    public ReviewQueryService(ReviewSnapshotRepository snapshotRepository,
                              ReviewDecisionRepository decisionRepository) {
        this.snapshotRepository = snapshotRepository;
        this.decisionRepository = decisionRepository;
    }

    public Optional<ReviewSnapshot> current(ReviewTaskId taskId) {
        return snapshotRepository.findCurrentByTask(taskId);
    }

    public ReviewSnapshot requireCurrent(ReviewTaskId taskId) {
        return current(taskId)
                .orElseThrow(() -> new EntityNotFoundException("任务还没有审阅快照：" + taskId.value()));
    }

    public List<ReviewDecision> decisions(ReviewSnapshotId snapshotId) {
        return decisionRepository.findBySnapshot(snapshotId);
    }
}
