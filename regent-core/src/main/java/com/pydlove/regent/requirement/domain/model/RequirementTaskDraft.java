package com.pydlove.regent.requirement.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

import java.util.List;

/** AI 拆解出的可编辑任务草稿。key 只在一条需求内有效。 */
public record RequirementTaskDraft(
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

    public RequirementTaskDraft {
        key = requireText(key, "任务草稿编号");
        title = requireText(title, "任务标题");
        objective = requireText(objective, "任务目标");
        scope = normalize(scope);
        outOfScope = normalize(outOfScope);
        acceptanceCriteria = normalize(acceptanceCriteria);
        dependencies = normalize(dependencies);
        risk = risk == null || risk.isBlank() ? "L0" : risk.trim();
        if (acceptanceCriteria.isEmpty()) {
            throw new DomainException("任务草稿必须包含验收标准：" + title);
        }
        if (dependencies.contains(key)) {
            throw new DomainException("任务不能依赖自身：" + key);
        }
    }

    private static List<String> normalize(List<String> values) {
        if (values == null) return List.of();
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new DomainException(field + "不能为空");
        }
        return value.trim();
    }
}
