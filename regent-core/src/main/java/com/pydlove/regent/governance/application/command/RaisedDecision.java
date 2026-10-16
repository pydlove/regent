package com.pydlove.regent.governance.application.command;

import com.pydlove.regent.governance.domain.model.ArbitrationResult;
import com.pydlove.regent.governance.domain.model.Decision;

import java.util.Objects;

/**
 * 提完一条决策的结果：决策单本身 + 它是怎么被定级的。
 *
 * <p>调用方（执行编排）只关心一件事 —— 现在该不该停下来等人。这个判断放在这里，
 * 而不是让调用方去读 {@code decision.getStatus()}：状态有四个取值，而「要不要停」只有两种，
 * 让每个调用点自己解释状态，迟早有一处把 AUTO_RESOLVED 也当成要停。
 */
public record RaisedDecision(Decision decision, ArbitrationResult arbitration) {

    public RaisedDecision {
        Objects.requireNonNull(decision, "决策单不能为空");
        Objects.requireNonNull(arbitration, "仲裁结果不能为空");
    }

    /** 需要人拍板：留在待批，任务应当阻塞。 */
    public boolean needsHuman() {
        return decision.isPending();
    }

    /** AI 已按分级规则自行决断并留痕，调用方可以带着结论立刻续跑。 */
    public boolean autoResolved() {
        return !decision.isPending();
    }
}
