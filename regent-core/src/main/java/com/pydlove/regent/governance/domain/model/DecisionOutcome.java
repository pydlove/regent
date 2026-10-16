package com.pydlove.regent.governance.domain.model;

import java.util.Objects;
import java.util.Optional;

/**
 * 一次决议的结论。
 *
 * <p>{@code choice} 与 {@code note} 都允许为空，但含义不同：choice 为空表示「采纳 AI 的建议」
 * （邮件里点一下就该能办成事，不该逼人先选一个选项），note 为空表示没留额外说明。
 * 唯一的例外是驳回 —— 理由必填，见 {@link Decision#reject}。
 */
public record DecisionOutcome(ResolvedBy by, String choice, String note) {

    public DecisionOutcome {
        Objects.requireNonNull(by, "决议人不能为空");
    }

    public static DecisionOutcome byAi(String choice, String note) {
        return new DecisionOutcome(ResolvedBy.AI, choice, note);
    }

    public static DecisionOutcome byHuman(String choice, String note) {
        return new DecisionOutcome(ResolvedBy.HUMAN, choice, note);
    }

    public Optional<String> choiceOrEmpty() {
        return Optional.ofNullable(choice);
    }

    public Optional<String> noteOrEmpty() {
        return Optional.ofNullable(note);
    }
}
