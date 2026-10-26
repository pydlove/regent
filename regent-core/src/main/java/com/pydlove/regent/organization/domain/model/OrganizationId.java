package com.pydlove.regent.organization.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

public record OrganizationId(long value) {

    public OrganizationId {
        if (value <= 0) {
            throw new DomainException("组织 ID 必须为正数");
        }
    }

    public static OrganizationId of(long value) {
        return new OrganizationId(value);
    }
}
