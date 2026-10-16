package com.pydlove.regent.execution.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

/** 任务 ID。执行单元在平台里是最被频繁引用的对象，Id 单独成类型收益最大。 */
public record TaskId(long value) {

    public TaskId {
        if (value <= 0) {
            throw new DomainException("任务 ID 必须为正数：" + value);
        }
    }

    public static TaskId of(long value) {
        return new TaskId(value);
    }

    @Override
    public String toString() {
        return Long.toString(value);
    }
}
