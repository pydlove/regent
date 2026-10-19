package com.pydlove.regent.execution.domain.repository;

import com.pydlove.regent.execution.domain.model.TaskAdjustment;
import com.pydlove.regent.execution.domain.model.TaskAdjustmentId;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.model.TaskRunId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 执行中调整方向的仓储接口。 */
public interface TaskAdjustmentRepository {

    Optional<TaskAdjustment> findById(TaskAdjustmentId id);

    /** 按创建顺序返回全部调整，供详情页复盘。 */
    List<TaskAdjustment> findByTask(TaskId taskId);

    /** 尚未交给执行器的调整，按创建顺序返回。 */
    List<TaskAdjustment> findPendingByTask(TaskId taskId);

    boolean hasPending(TaskId taskId);

    TaskAdjustment save(TaskAdjustment adjustment);

    void markApplied(TaskAdjustmentId id, TaskRunId runId, Instant appliedAt);
}
