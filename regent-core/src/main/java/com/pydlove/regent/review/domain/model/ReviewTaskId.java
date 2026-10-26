package com.pydlove.regent.review.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

/** 审阅上下文对任务的本地强类型引用，不跨上下文持有 Task 实体。 */
public record ReviewTaskId(long value) {

    public ReviewTaskId {
        if (value < 1) {
            throw new DomainException("审阅任务 ID 必须为正数：" + value);
        }
    }

    public static ReviewTaskId of(long value) {
        return new ReviewTaskId(value);
    }
}
