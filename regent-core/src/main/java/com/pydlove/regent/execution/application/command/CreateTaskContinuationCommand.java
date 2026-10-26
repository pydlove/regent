package com.pydlove.regent.execution.application.command;

import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.model.CodeStandardId;

import java.util.List;

/**
 * 基于已有任务继续创建一条新任务，并复用原任务的 Codex 会话。
 *
 * @param sourceTaskId 已建立 Codex 会话的来源任务
 * @param prompt       新任务要完成的增量开发内容
 * @param priority     新任务的派发优先级
 */
public record CreateTaskContinuationCommand(
        TaskId sourceTaskId,
        String prompt,
        int priority,
        List<CodeStandardId> codeStandardIds) {

    public CreateTaskContinuationCommand {
        codeStandardIds = codeStandardIds == null ? null : List.copyOf(codeStandardIds);
    }

    public CreateTaskContinuationCommand(TaskId sourceTaskId, String prompt, int priority) {
        this(sourceTaskId, prompt, priority, null);
    }
}
