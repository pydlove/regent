package com.pydlove.regent.requirement.interfaces.dto;

import com.pydlove.regent.requirement.domain.model.RequirementTaskDraft;

import java.util.List;

/** 用户在确认页编辑后的任务草稿。 */
public record RequirementTaskDraftRequest(
        String key,
        String title,
        String objective,
        List<String> scope,
        List<String> outOfScope,
        List<String> acceptanceCriteria,
        List<String> dependencies,
        Integer priority,
        String risk,
        Integer suggestedOrder) {

    public RequirementTaskDraft toDomain() {
        return new RequirementTaskDraft(
                key,
                title,
                objective,
                scope,
                outOfScope,
                acceptanceCriteria,
                dependencies,
                priority == null ? 0 : priority,
                risk,
                suggestedOrder == null ? 0 : suggestedOrder);
    }
}
