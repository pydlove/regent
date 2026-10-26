package com.pydlove.regent.requirement.domain.model;

import java.util.List;

/** Codex 根据想法生成的需求池草稿。 */
public record RequirementIdeaDraft(
        String title,
        String rawText,
        String acceptanceText,
        List<String> questions,
        List<String> assumptions) {

    public RequirementIdeaDraft {
        questions = questions == null ? List.of() : List.copyOf(questions);
        assumptions = assumptions == null ? List.of() : List.copyOf(assumptions);
    }
}
