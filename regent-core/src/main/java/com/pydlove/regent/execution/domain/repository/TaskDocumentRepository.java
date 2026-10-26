package com.pydlove.regent.execution.domain.repository;

import com.pydlove.regent.execution.domain.model.TaskDocument;
import com.pydlove.regent.execution.domain.model.TaskDocumentId;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.identity.domain.model.UserId;

import java.util.List;
import java.util.Optional;

/** 任务文档仓储。 */
public interface TaskDocumentRepository {

    TaskDocument save(TaskDocument document);

    Optional<TaskDocument> findById(TaskDocumentId id);

    Optional<TaskDocument> findByIdOwned(TaskDocumentId id, UserId ownerId);

    List<TaskDocument> findByTask(TaskId taskId);
}
