package com.pydlove.regent.project.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

/** 项目版本来源。 */
public enum ProjectVersionSource {
    GIT_TAG,
    MANUAL;

    public static ProjectVersionSource parse(String value) {
        if (value == null || value.isBlank()) {
            return GIT_TAG;
        }
        try {
            return valueOf(value.strip().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new DomainException("不支持的版本来源：" + value, exception);
        }
    }
}
