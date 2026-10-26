package com.pydlove.regent.requirement.domain.model;

import java.util.List;

/** 一次需求分析结果：有任务表示可确认，无任务时展示澄清问题。 */
public record RequirementPlanningResult(
        List<String> questions,
        List<String> assumptions,
        List<RequirementTaskDraft> taskDrafts) {

    public RequirementPlanningResult {
        questions = questions == null ? List.of() : List.copyOf(questions);
        assumptions = assumptions == null ? List.of() : List.copyOf(assumptions);
        taskDrafts = taskDrafts == null ? List.of() : List.copyOf(taskDrafts);
    }
}
