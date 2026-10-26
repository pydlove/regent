package com.pydlove.regent.delivery.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

/** 立项会议纪要附件 ID。 */
public record InitiativeAttachmentId(long value) {

    public InitiativeAttachmentId {
        if (value <= 0) {
            throw new DomainException("立项附件 ID 必须为正数：" + value);
        }
    }

    public static InitiativeAttachmentId of(long value) {
        return new InitiativeAttachmentId(value);
    }
}
