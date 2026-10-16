package com.pydlove.regent.execution.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

/** 附件 ID。与其它强类型 ID 一样包成值对象，避免把附件 ID 与任务 ID 传串。 */
public record AttachmentId(long value) {

    public AttachmentId {
        if (value <= 0) {
            throw new DomainException("附件 ID 必须为正数：" + value);
        }
    }

    public static AttachmentId of(long value) {
        return new AttachmentId(value);
    }

    @Override
    public String toString() {
        return Long.toString(value);
    }
}
