package com.pydlove.regent.execution.domain.repository;

import com.pydlove.regent.execution.domain.model.TaskCodeStandard;
import com.pydlove.regent.execution.domain.model.TaskCodeStandardId;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.identity.domain.model.UserId;

import java.util.List;
import java.util.Optional;

/** 任务与开发规范版本的绑定快照仓储。 */
public interface TaskCodeStandardRepository {

    TaskCodeStandard save(TaskCodeStandard standard);

    List<TaskCodeStandard> findByTask(TaskId taskId);

    Optional<TaskCodeStandard> findByIdOwned(TaskCodeStandardId id, UserId ownerId);
}
