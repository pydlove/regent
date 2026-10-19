package com.pydlove.regent.execution.domain.repository;

import com.pydlove.regent.execution.domain.model.AttachmentId;
import com.pydlove.regent.execution.domain.model.TaskAttachment;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.identity.domain.model.UserId;

import java.util.List;
import java.util.Optional;

/** 附件仓储。附着在任务上，任务是它的聚合边界。 */
public interface TaskAttachmentRepository {

    /**
     * 新增并返回带主键的实例。
     *
     * <p>与 {@code TaskRepository.save}（返回 void、靠 assignIdentity 原地回填）不同：
     * 附件是不可变 record，回填只能返回新实例。这个差异来自「有没有状态机」，不是风格偏好。
     */
    TaskAttachment save(TaskAttachment attachment);

    Optional<TaskAttachment> findById(AttachmentId id);

    default Optional<TaskAttachment> findByIdOwned(AttachmentId id, UserId ownerId) {
        return findById(id);
    }

    /** 某个任务的全部附件，按上传顺序（ID 升序）。 */
    List<TaskAttachment> findByTask(TaskId taskId);
}
