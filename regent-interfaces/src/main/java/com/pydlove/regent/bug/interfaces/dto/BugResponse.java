package com.pydlove.regent.bug.interfaces.dto;

import com.pydlove.regent.bug.domain.repository.BugView;

import java.time.Instant;

/** 缺陷单对外契约。 */
public record BugResponse(
        long id,
        long projectId,
        String projectName,
        Long requirementId,
        Long sourceTaskId,
        Long fixTaskId,
        String source,
        String title,
        String severity,
        String status,
        String reproduction,
        String filePath,
        String detail,
        Instant foundAt,
        Instant fixedAt,
        String wontFixReason) {

    public static BugResponse from(BugView view) {
        return new BugResponse(
                view.id(),
                view.projectId(),
                view.projectName(),
                view.requirementId(),
                view.sourceTaskId(),
                view.fixTaskId(),
                view.source().name(),
                view.title(),
                view.severity().name(),
                view.status().name(),
                view.reproduction(),
                view.filePath(),
                view.detail(),
                view.foundAt(),
                view.fixedAt(),
                view.wontFixReason());
    }
}
