package com.pydlove.regent.execution.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

/** 任务文档 ID。 */
public record TaskDocumentId(long value) {

    public TaskDocumentId {
        if (value <= 0) {
            throw new DomainException("任务文档 ID 必须为正数：" + value);
        }
    }

    public static TaskDocumentId of(long value) {
        return new TaskDocumentId(value);
    }
}
