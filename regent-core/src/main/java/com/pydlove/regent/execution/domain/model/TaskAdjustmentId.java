package com.pydlove.regent.execution.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

/** 执行中调整方向的强类型 ID。 */
public record TaskAdjustmentId(long value) {

    public TaskAdjustmentId {
        if (value <= 0) {
            throw new DomainException("调整方向 ID 必须为正数：" + value);
        }
    }

    public static TaskAdjustmentId of(long value) {
        return new TaskAdjustmentId(value);
    }

    @Override
    public String toString() {
        return Long.toString(value);
    }
}
