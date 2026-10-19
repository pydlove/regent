package com.pydlove.regent.deployment.domain.model;

/** 部署脚本的一处规范问题。行号从 1 开始。 */
public record DeploymentScriptIssue(
        int line,
        String code,
        String message) {
}
