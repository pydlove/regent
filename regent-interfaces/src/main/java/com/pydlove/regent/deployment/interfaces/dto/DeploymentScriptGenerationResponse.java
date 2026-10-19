package com.pydlove.regent.deployment.interfaces.dto;

import com.pydlove.regent.deployment.application.query.GeneratedDeploymentScript;

/** 生成结果直接对应部署表单中的脚本名称与正文。 */
public record DeploymentScriptGenerationResponse(
        String scriptFilename,
        String scriptContent) {

    public static DeploymentScriptGenerationResponse from(GeneratedDeploymentScript generated) {
        return new DeploymentScriptGenerationResponse(
                generated.scriptFilename(), generated.scriptContent());
    }
}
