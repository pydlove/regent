package com.pydlove.regent.governance.domain.model;

import com.pydlove.regent.shared.kernel.DecisionLevel;

import java.util.List;
import java.util.Objects;

/**
 * 仲裁输入 —— 一次决策需要仲裁时，把判断所需的事实一次性交齐。
 *
 * @param projectId     所属项目；用于筛掉别的项目的规则
 * @param aiLevel       执行器自评的等级
 * @param autoThreshold 项目级自动决策阈值，来自 {@code project.default_decision_level}
 * @param changedPaths  本次改动涉及的文件路径（相对项目根），用于路径通配匹配
 * @param decisionText  决策问题与摘要文本，用于关键词匹配
 * @param reversible    改动是否可逆；未知时传 null
 * @param rules         候选规则；仲裁器会自行筛掉停用与非本项目的
 */
public record ArbitrationInput(
        Long projectId,
        DecisionLevel aiLevel,
        DecisionLevel autoThreshold,
        List<String> changedPaths,
        String decisionText,
        Boolean reversible,
        List<DecisionRule> rules) {

    public ArbitrationInput {
        Objects.requireNonNull(aiLevel, "AI 自评等级不能为空");
        Objects.requireNonNull(autoThreshold, "自动决策阈值不能为空");
        changedPaths = changedPaths == null ? List.of() : List.copyOf(changedPaths);
        decisionText = decisionText == null ? "" : decisionText;
        rules = rules == null ? List.of() : List.copyOf(rules);
    }
}
