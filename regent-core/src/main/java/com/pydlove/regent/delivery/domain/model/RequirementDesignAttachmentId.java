package com.pydlove.regent.delivery.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

public record RequirementDesignAttachmentId(long value) {
    public RequirementDesignAttachmentId {
        if (value <= 0) throw new DomainException("设计附件 ID 必须为正数");
    }

    public static RequirementDesignAttachmentId of(long value) {
        return new RequirementDesignAttachmentId(value);
    }
}
