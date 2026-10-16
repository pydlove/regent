package com.pydlove.regent.execution.domain.repository;

import com.pydlove.regent.execution.domain.model.TaskSource;
import com.pydlove.regent.execution.domain.model.TaskStatus;
import com.pydlove.regent.governance.domain.model.DecisionId;
import com.pydlove.regent.shared.kernel.DecisionLevel;

import java.time.Instant;

/**
 * 任务列表行 —— 读模型，不是聚合。
 *
 * <p>列里有几类字段不属于 {@code task} 表：{@code level}（该任务最新决策的仲裁等级）、
 * {@code tokens}（历次执行的 token 汇总）与 {@code executionMillis}（历次实际执行时长之和）。
 * 为了显示它们去给聚合加字段是错的 —— 聚合的字段应该由「状态机需要知道什么」决定，
 * 而不是「列表想显示什么」。
 */
public record TaskListItem(
        long id,
        long projectId,
        String projectName,
        TaskSource source,
        String title,
        TaskStatus status,
        int priority,
        DecisionLevel level,
        String branchName,
        DecisionId blockingDecisionId,
        int attemptCount,
        long tokens,
        /** 所有执行记录的运行时长之和；阻塞等人或排队等待的时间不计入。 */
        long executionMillis,
        Instant startedAt,
        Instant finishedAt,
        Instant createdAt,
        Instant updatedAt) {

    /** 尚无决策的任务按 L0 展示：它还没被任何规则抬高过。 */
    public boolean isBlocked() {
        return status == TaskStatus.BLOCKED;
    }
}
