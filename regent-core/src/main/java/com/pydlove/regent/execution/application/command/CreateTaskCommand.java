package com.pydlove.regent.execution.application.command;

import com.pydlove.regent.project.domain.model.ProjectId;

/**
 * 人工录入任务的入参。
 *
 * <p>没有 {@code title}：标题由应用服务从指令首行推导（见 {@code TaskCommandService}）。
 * 让人为同一条任务写两遍说法（标题 + 指令），最后多半是把指令的前十个字复制到标题里。
 *
 * @param projectId 任务归属的项目；项目不存在时应用服务直接拒绝
 * @param prompt    交给 codex 的完整指令
 * @param priority  派发优先级，数值越大越先跑
 */
public record CreateTaskCommand(
        ProjectId projectId,
        String prompt,
        int priority) {
}
