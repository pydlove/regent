package com.pydlove.regent.execution.application.command;

import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.identity.domain.model.UserId;

/**
 * 给执行中的任务追加一个调整方向。
 *
 * <p>{@code sourceTaskId} 与 {@code temporaryContent} 必须二选一。
 */
public record AddTaskAdjustmentCommand(
        TaskId taskId,
        UserId ownerId,
        TaskId sourceTaskId,
        String temporaryContent) {
}
