package com.pydlove.regent.requirement.application.query;

import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.requirement.domain.model.Requirement;
import com.pydlove.regent.requirement.domain.model.RequirementStatus;

import java.util.List;
import java.util.Map;

/** 需求详情与关联任务。 */
public record RequirementView(
        Requirement requirement,
        RequirementStatus effectiveStatus,
        List<Task> tasks,
        Map<Long, List<Long>> dependencies) {

    public RequirementView {
        tasks = List.copyOf(tasks);
        dependencies = Map.copyOf(dependencies);
    }
}
