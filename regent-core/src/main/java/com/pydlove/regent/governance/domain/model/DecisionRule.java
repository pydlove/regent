package com.pydlove.regent.governance.domain.model;

import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.kernel.DecisionLevel;

import java.util.Objects;

/**
 * 一条分级规则 —— 仲裁器的一等输入。
 *
 * <p>本类只承载规则数据，匹配逻辑集中在 {@link
 * com.pydlove.regent.governance.domain.service.DecisionArbiter}。这样仲裁器是唯一被测试覆盖的
 * 匹配实现，不存在「规则自己会匹配、仲裁器又匹配一次」的双份逻辑。
 *
 * @param id          规则主键；尚未持久化时为 null
 * @param projectId   所属项目；null 表示全局规则，对所有项目生效
 * @param name        规则名，用于审计留痕与排错
 * @param type        匹配方式
 * @param pattern     通配表达式 / 关键词 / true 或 false，含义随 type 而定
 * @param targetLevel 命中后采用的等级
 * @param priority    数值越大越先匹配；相同优先级按 id 升序，保证仲裁结果可复现
 * @param enabled     停用开关；停用的规则不参与匹配
 */
public record DecisionRule(
        Long id,
        Long projectId,
        String name,
        RuleType type,
        String pattern,
        DecisionLevel targetLevel,
        int priority,
        boolean enabled) {

    public DecisionRule {
        Objects.requireNonNull(name, "规则名不能为空");
        Objects.requireNonNull(type, "规则类型不能为空");
        Objects.requireNonNull(pattern, "规则 pattern 不能为空");
        Objects.requireNonNull(targetLevel, "规则目标等级不能为空");
        if (pattern.isBlank()) {
            throw new DomainException("规则[" + name + "]的 pattern 不能为空白");
        }
    }

    /** 是否对所有项目生效。 */
    public boolean isGlobal() {
        return projectId == null;
    }
}
