package com.pydlove.regent.execution.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

/** 平台支持的一键安装方式。命令固定，不接受用户传入任意参数。 */
public enum ExecutorInstallMethod {
    NPM,
    BREW;

    public static ExecutorInstallMethod parse(String value) {
        if (value == null || value.isBlank()) {
            throw new DomainException("安装方式不能为空");
        }
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new DomainException("不支持的安装方式：" + value);
        }
    }
}
