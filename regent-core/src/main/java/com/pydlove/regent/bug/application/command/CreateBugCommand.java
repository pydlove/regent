package com.pydlove.regent.bug.application.command;

import com.pydlove.regent.bug.domain.model.BugSeverity;
import com.pydlove.regent.project.domain.model.ProjectId;

/** 人工录入缺陷单。 */
public record CreateBugCommand(
        ProjectId projectId,
        String title,
        BugSeverity severity,
        String reproduction,
        String filePath,
        String detail) {
}
