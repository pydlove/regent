package com.pydlove.regent.governance.infrastructure.persistence;

import lombok.Data;

/**
 * {@code decision_rule} 表的一行。
 *
 * <p>{@code ruleType} / {@code targetLevel} 保持字符串：解释它们（枚举解析、未知值如何处置）
 * 是仓储实现的事，数据对象只承担「表的形状」。
 */
@Data
public class DecisionRuleRow {

    private Long id;
    private Long projectId;
    private String name;
    private String ruleType;
    private String pattern;
    private String targetLevel;
    private Integer priority;
    private Boolean enabled;
}
