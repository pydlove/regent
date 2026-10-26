package com.pydlove.regent.delivery.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

/** 产品版本 ID。 */
public record ProductVersionId(long value) {

    public ProductVersionId {
        if (value <= 0) {
            throw new DomainException("产品版本 ID 必须为正数：" + value);
        }
    }

    public static ProductVersionId of(long value) {
        return new ProductVersionId(value);
    }
}
