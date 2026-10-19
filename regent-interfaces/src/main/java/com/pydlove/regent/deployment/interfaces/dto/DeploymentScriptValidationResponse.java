package com.pydlove.regent.deployment.interfaces.dto;

import java.util.List;

/** 部署脚本检测结论；issues 为空才允许保存和执行。 */
public record DeploymentScriptValidationResponse(
        boolean valid,
        List<DeploymentScriptIssueResponse> issues) {
}
