package com.pydlove.regent.governance.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

/**
 * 决策单 ID。
 *
 * <p>决策单的编号对使用者是可见的（邮件通知、审批链接里都会出现），
 * 所以它的字符串形式是展示契约的一部分，不要随意改。
 */
public record DecisionId(long value) {

    public DecisionId {
        if (value <= 0) {
            throw new DomainException("决策 ID 必须为正数：" + value);
        }
    }

    public static DecisionId of(long value) {
        return new DecisionId(value);
    }

    @Override
    public String toString() {
        return Long.toString(value);
    }
}
