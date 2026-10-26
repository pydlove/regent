package com.pydlove.regent.review.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

/** 审阅上下文对执行记录的本地强类型引用。 */
public record ReviewTaskRunId(long value) {

    public ReviewTaskRunId {
        if (value < 1) {
            throw new DomainException("审阅执行记录 ID 必须为正数：" + value);
        }
    }

    public static ReviewTaskRunId of(long value) {
        return new ReviewTaskRunId(value);
    }
}
