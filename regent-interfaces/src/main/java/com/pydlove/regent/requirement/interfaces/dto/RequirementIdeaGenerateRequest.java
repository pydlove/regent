package com.pydlove.regent.requirement.interfaces.dto;

public record RequirementIdeaGenerateRequest(
        Long projectId,
        Long initiativeId,
        String idea) {
}
