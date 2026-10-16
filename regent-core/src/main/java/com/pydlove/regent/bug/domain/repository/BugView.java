package com.pydlove.regent.bug.domain.repository;

import com.pydlove.regent.bug.domain.model.BugSeverity;
import com.pydlove.regent.bug.domain.model.BugSource;
import com.pydlove.regent.bug.domain.model.BugStatus;

import java.time.Instant;

/** 缺陷单读模型。 */
public record BugView(
        long id,
        long projectId,
        String projectName,
        Long requirementId,
        Long sourceTaskId,
        Long fixTaskId,
        BugSource source,
        String title,
        BugSeverity severity,
        BugStatus status,
        String reproduction,
        String filePath,
        String detail,
        Instant foundAt,
        Instant fixedAt,
        String wontFixReason) {
}
