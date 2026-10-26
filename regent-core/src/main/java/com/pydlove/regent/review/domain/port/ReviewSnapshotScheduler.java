package com.pydlove.regent.review.domain.port;

import com.pydlove.regent.review.domain.model.ReviewTaskId;
import com.pydlove.regent.review.domain.model.ReviewTaskRunId;

/** 任务成功收场后异步生成审阅快照，失败不得回滚任务。 */
public interface ReviewSnapshotScheduler {

    void scheduleAfterTaskCompleted(ReviewTaskId taskId, ReviewTaskRunId taskRunId);
}
