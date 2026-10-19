package com.pydlove.regent.execution.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

/** 任务草稿强类型 ID。 */
public record TaskDraftId(long value) {

    public TaskDraftId {
        if (value <= 0) {
            throw new DomainException("任务草稿 ID 必须为正数：" + value);
        }
    }

    public static TaskDraftId of(long value) {
        return new TaskDraftId(value);
    }

    @Override
    public String toString() {
        return Long.toString(value);
    }
}
