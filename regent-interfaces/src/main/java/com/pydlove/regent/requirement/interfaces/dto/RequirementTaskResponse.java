package com.pydlove.regent.requirement.interfaces.dto;

import com.pydlove.regent.execution.domain.model.Task;

import java.util.List;

/** 需求详情中的关联任务。 */
public record RequirementTaskResponse(
        long id,
        String title,
        String status,
        int priority,
        List<Long> dependencies) {

    public static RequirementTaskResponse from(Task task, List<Long> dependencies) {
        return new RequirementTaskResponse(
                task.getId().value(),
                task.getTitle(),
                task.getStatus().name(),
                task.getPriority(),
                dependencies);
    }
}
