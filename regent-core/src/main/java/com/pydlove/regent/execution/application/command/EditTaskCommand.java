package com.pydlove.regent.execution.application.command;

import com.pydlove.regent.execution.domain.model.TaskId;

/**
 * 编辑任务的可变内容。
 *
 * <p>项目归属不在编辑范围内：任务一旦执行过，分支、会话和审计记录都绑定在原项目上，
 * 中途换项目会让这些事实失去同一工作区这一前提。
 */
public record EditTaskCommand(
        TaskId taskId,
        String prompt,
        Integer priority) {
}
