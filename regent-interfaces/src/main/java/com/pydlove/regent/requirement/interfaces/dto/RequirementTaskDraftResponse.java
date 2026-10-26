package com.pydlove.regent.requirement.interfaces.dto;

import com.pydlove.regent.requirement.domain.model.RequirementTaskDraft;

import java.util.List;

/** 需求拆解任务草稿的对外契约。 */
public record RequirementTaskDraftResponse(
        String key,
        String title,
        String objective,
        List<String> scope,
        List<String> outOfScope,
        List<String> acceptanceCriteria,
        List<String> dependencies,
        int priority,
        String risk,
        int suggestedOrder) {

    public static RequirementTaskDraftResponse from(RequirementTaskDraft draft) {
        return new RequirementTaskDraftResponse(
                draft.key(),
                draft.title(),
                draft.objective(),
                draft.scope(),
                draft.outOfScope(),
                draft.acceptanceCriteria(),
                draft.dependencies(),
                draft.priority(),
                draft.risk(),
                draft.suggestedOrder());
    }
}
