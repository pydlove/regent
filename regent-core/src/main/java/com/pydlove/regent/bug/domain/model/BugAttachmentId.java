package com.pydlove.regent.bug.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

/** Bug 图片附件 ID。 */
public record BugAttachmentId(long value) {

    public BugAttachmentId {
        if (value <= 0) {
            throw new DomainException("Bug 附件 ID 必须为正数：" + value);
        }
    }

    public static BugAttachmentId of(long value) {
        return new BugAttachmentId(value);
    }

    @Override
    public String toString() {
        return Long.toString(value);
    }
}
