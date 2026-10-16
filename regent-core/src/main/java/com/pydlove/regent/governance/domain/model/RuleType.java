package com.pydlove.regent.governance.domain.model;

/**
 * 分级规则的匹配方式。
 *
 * <p>{@code decision_rule.rule_type} 在库里是自由文本 VARCHAR 而非 MySQL ENUM，所以这个枚举
 * 可以随实现演进，不需要迁移脚本。V1 的表注释里还提到过一个 {@code CHANGED_FILES} 类型，
 * 实际实现中它与 {@code PATH_GLOB} 语义重合（都是对改动路径做通配），已合并取消，
 * 避免同一种规则有两种写法。
 */
public enum RuleType {

    /** 用通配表达式匹配改动过的文件路径。写法沿用 Ant 习惯，如 deploy 目录下的任意文件。 */
    PATH_GLOB,

    /** 用关键词匹配决策问题与摘要文本，忽略大小写。 */
    KEYWORD,

    /** 按改动是否可逆匹配。pattern 只接受 true 或 false。 */
    REVERSIBILITY
}
