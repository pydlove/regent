package com.pydlove.regent.deployment.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

/** 部署脚本的执行位置。 */
public enum DeploymentExecutionMode {

    /** 平台通过 SSH 把脚本文本送到服务器，在配置的远程目录执行。 */
    REMOTE_SCRIPT,

    /** 平台在归属项目根目录直接执行项目内的发布脚本，由脚本完成构建、上传和重启。 */
    PROJECT_SCRIPT;

    public static DeploymentExecutionMode parse(String value) {
        if (value == null || value.isBlank()) {
            return REMOTE_SCRIPT;
        }
        try {
            return valueOf(value.trim());
        } catch (IllegalArgumentException e) {
            throw new DomainException("未知的部署执行方式：" + value);
        }
    }
}
