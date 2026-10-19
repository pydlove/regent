package com.pydlove.regent.deployment.interfaces.dto;

import com.pydlove.regent.deployment.domain.model.DeploymentScriptIssue;

/** 部署脚本的一处规范问题。 */
public record DeploymentScriptIssueResponse(
        int line,
        String code,
        String message) {

    public static DeploymentScriptIssueResponse from(DeploymentScriptIssue issue) {
        return new DeploymentScriptIssueResponse(issue.line(), issue.code(), issue.message());
    }
}
