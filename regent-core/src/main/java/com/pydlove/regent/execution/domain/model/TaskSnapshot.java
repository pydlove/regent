package com.pydlove.regent.execution.domain.model;

import com.pydlove.regent.bug.domain.model.BugId;
import com.pydlove.regent.governance.domain.model.DecisionId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.requirement.domain.model.RequirementId;

import java.time.Instant;

/**
 * 任务整行快照，只用于「从存储重建领域对象」。
 *
 * <p>把十七个字段打包成一个记录，是为了让 {@code Task.restore} 的签名可读，
 * 而不是让仓储去调一串重建用的 setter —— 那等于把聚合的封装撕开一个永久的口子。
 *
 * <p>可空字段（{@code requirementId}、{@code bugId}、{@code codexThreadId}、{@code branchName}、
 * {@code blockingDecisionId}、{@code startedAt}、{@code finishedAt}）用 null 表示「数据库里就是 NULL」，
 * 这是与表结构的一对一映射，转换发生在基础设施层。
 */
public record TaskSnapshot(
        TaskId id,
        ProjectId projectId,
        RequirementId requirementId,
        BugId bugId,
        TaskSource source,
        String title,
        String prompt,
        int priority,
        TaskStatus status,
        String codexThreadId,
        String branchName,
        DecisionId blockingDecisionId,
        int attemptCount,
        Instant startedAt,
        Instant finishedAt,
        Instant createdAt,
        Instant updatedAt) {
}
