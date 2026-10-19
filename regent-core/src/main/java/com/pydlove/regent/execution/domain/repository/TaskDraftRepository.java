package com.pydlove.regent.execution.domain.repository;

import com.pydlove.regent.execution.domain.model.TaskDraft;
import com.pydlove.regent.identity.domain.model.UserId;

import java.util.Optional;

/** 当前用户的单份任务草稿仓储。 */
public interface TaskDraftRepository {

    Optional<TaskDraft> findByUserId(UserId userId);

    /** 同一用户只保留一份草稿；已存在时整体覆盖可编辑字段。 */
    void save(TaskDraft draft);

    void deleteByUserId(UserId userId);
}
