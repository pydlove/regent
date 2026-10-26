package com.pydlove.regent.delivery.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

public record RequirementDesignModuleId(long value) {
    public RequirementDesignModuleId {
        if (value <= 0) throw new DomainException("设计模块 ID 必须为正数");
    }

    public static RequirementDesignModuleId of(long value) {
        return new RequirementDesignModuleId(value);
    }
}
