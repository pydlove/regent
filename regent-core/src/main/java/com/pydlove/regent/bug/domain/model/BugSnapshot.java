package com.pydlove.regent.bug.domain.model;

import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.requirement.domain.model.RequirementId;

import java.time.Instant;

/** 从存储重建缺陷单所需的最小快照。 */
public record BugSnapshot(
        BugId id,
        ProjectId projectId,
        RequirementId requirementId,
        TaskId sourceTaskId,
        TaskId fixTaskId,
        BugSource source,
        String title,
        BugSeverity severity,
        BugStatus status,
        String reproduction,
        String filePath,
        String detail,
        Instant foundAt,
        Instant fixedAt,
        String wontFixReason,
        Instant createdAt,
        Instant updatedAt) {
}
