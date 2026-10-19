package com.pydlove.regent.execution.infrastructure.persistence;

import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** 调整方向映射接口。SQL 在 {@code mapper/TaskAdjustmentMapper.xml}。 */
public interface TaskAdjustmentMapper {

    TaskAdjustmentData selectById(@Param("id") long id);

    List<TaskAdjustmentData> selectByTask(@Param("taskId") long taskId);

    List<TaskAdjustmentData> selectPendingByTask(@Param("taskId") long taskId);

    int countPending(@Param("taskId") long taskId);

    int insert(TaskAdjustmentData data);

    int markApplied(@Param("id") long id,
                    @Param("runId") long runId,
                    @Param("appliedAt") LocalDateTime appliedAt);
}
