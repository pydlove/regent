package com.pydlove.regent.execution.domain.model;

import com.pydlove.regent.shared.kernel.DecisionLevel;

import java.util.List;
import java.util.Objects;

/**
 * 执行器回报的「我需要一个决策」。
 *
 * <p>这是防腐层翻译后的形状：{@code execution} 只知道自己需要一个带等级的问题，
 * 至于 codex 是用 JSON Schema 还是别的形式报出来的，与这里无关。
 *
 * @param level          执行器自评等级；最终生效等级由仲裁器就高不就低地定
 * @param question       需要拍板的问题
 * @param options        候选方案，可为空
 * @param recommendation 执行器的建议，可为空
 * @param reversible     改动是否可逆；不可逆的改动不许 AI 自主决断
 * @param blastRadius    影响范围描述，可为空
 */
public record ExecDecision(
        DecisionLevel level,
        String question,
        List<String> options,
        String recommendation,
        boolean reversible,
        String blastRadius) {

    public ExecDecision {
        Objects.requireNonNull(level, "执行器自评等级不能为空");
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("待决策问题不能为空");
        }
        options = options == null ? List.of() : List.copyOf(options);
    }
}
