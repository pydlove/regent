package com.pydlove.regent.shared.kernel;

import com.pydlove.regent.shared.domain.DomainException;

import java.util.Locale;
import java.util.Objects;

/**
 * 决策等级 —— 分级决策机制的核心概念，属于共享内核。
 *
 * <p><b>本枚举刻意不回答「哪个等级可以自动决策」。</b>那由项目级的自动阈值决定
 * （{@code decision_rule} 之外的 {@code project.default_decision_level}），因为不同项目的
 * 风险容忍度不一样。本枚举只负责一件事：等级之间的大小关系。
 *
 * <p><b>等级只升不降。</b>{@link #higherOf} 是仲裁「就高不就低」的原子操作，它保证任何规则
 * 都无法把 AI 自评的等级压下去。这是整套决策机制的安全基石，原因见 DecisionArbiter 的类注释。
 */
public enum DecisionLevel {

    /** 完全自主：改文案、加日志、补注释、文档目录下改动。AI 决定，只记录不打扰。 */
    L0(0, "完全自主"),

    /** 自主但进日报：命名、内部结构、小版本依赖、错误提示措辞。 */
    L1(1, "自主但进日报"),

    /** 需人工批准：接口签名变更、DB 字段增减、跨模块改动、新增依赖。挂起并推送。 */
    L2(2, "需人工批准"),

    /** 强阻塞：删数据删表、权限鉴权、上线发版、打包流程、密钥证书。挂起并强提醒。 */
    L3(3, "强阻塞");

    private final int weight;
    private final String description;

    DecisionLevel(int weight, String description) {
        this.weight = weight;
        this.description = description;
    }

    public int weight() {
        return weight;
    }

    public String description() {
        return description;
    }

    /**
     * 取两者中更高的等级。
     *
     * <p>注意语义：结果永远不会低于 {@code this}。规则只可能把等级抬高。
     */
    public DecisionLevel higherOf(DecisionLevel other) {
        Objects.requireNonNull(other, "other 不能为空");
        return this.weight >= other.weight ? this : other;
    }

    public boolean isHigherThan(DecisionLevel other) {
        Objects.requireNonNull(other, "other 不能为空");
        return this.weight > other.weight;
    }

    public boolean isAtLeast(DecisionLevel other) {
        Objects.requireNonNull(other, "other 不能为空");
        return this.weight >= other.weight;
    }

    /** 是否落在自动决策阈值之内（含边界）。阈值本身也是可自动决策的。 */
    public boolean isWithin(DecisionLevel autoThreshold) {
        Objects.requireNonNull(autoThreshold, "autoThreshold 不能为空");
        return this.weight <= autoThreshold.weight;
    }

    /**
     * 解析等级文本。同时接受 {@code L2} 与 {@code 2} 两种写法 —— 执行器返回的结构化出参
     * 未必严格遵循约定，宽松一点比让整个决策流程失败划算。但除此之外一律拒绝。
     */
    public static DecisionLevel parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new DomainException("决策等级不能为空");
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "L0", "0" -> L0;
            case "L1", "1" -> L1;
            case "L2", "2" -> L2;
            case "L3", "3" -> L3;
            default -> throw new DomainException("无法识别的决策等级：" + raw);
        };
    }
}
