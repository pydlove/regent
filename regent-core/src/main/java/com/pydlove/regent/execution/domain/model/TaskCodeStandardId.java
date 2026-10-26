package com.pydlove.regent.execution.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

/** 任务开发规范快照 ID。 */
public record TaskCodeStandardId(long value) {

    public TaskCodeStandardId {
        if (value <= 0) {
            throw new DomainException("任务开发规范快照 ID 必须为正数：" + value);
        }
    }

    public static TaskCodeStandardId of(long value) {
        return new TaskCodeStandardId(value);
    }
}
