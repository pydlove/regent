package com.pydlove.regent.deployment.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

import java.util.Locale;

/** 部署连服务器时使用的认证方式。密码永不进入这些模型。 */
public enum DeploymentAuthType {

    KEY,
    PASSWORD;

    public static DeploymentAuthType parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new DomainException("部署认证方式不能为空");
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new DomainException("无法识别的部署认证方式：" + raw);
        }
    }
}
