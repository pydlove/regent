package com.pydlove.regent.review.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

import java.util.List;

/** 预审输入的领域快照，不携带执行器或 HTTP 类型。 */
public record ReviewAnalysisRequest(
        ReviewTaskId taskId,
        String projectDirectory,
        String taskTitle,
        String taskPrompt,
        ReviewDiff diff,
        List<ReviewEvidence> evidence) {

    public ReviewAnalysisRequest {
        if (taskTitle == null || taskTitle.isBlank()) {
            throw new DomainException("预审任务标题不能为空");
        }
        if (projectDirectory == null || projectDirectory.isBlank()) {
            throw new DomainException("预审项目目录不能为空");
        }
        if (diff == null) {
            throw new DomainException("预审 diff 不能为空");
        }
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }
}
