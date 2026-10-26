package com.pydlove.regent.identity.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

import java.util.regex.Pattern;

/** 一次 App 安装实例的稳定标识，不属于业务租户，也不能跨账号复用登录状态。 */
public record InstallationId(String value) {

    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{7,127}");

    public InstallationId {
        if (value == null || !SAFE.matcher(value).matches()) {
            throw new DomainException("手机安装标识格式不正确");
        }
    }

    public static InstallationId of(String value) {
        return new InstallationId(value);
    }
}
