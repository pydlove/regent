package com.pydlove.regent.deployment.application.query;

/** 可直接放入新建部署表单的生成结果。 */
public record GeneratedDeploymentScript(
        String scriptFilename,
        String scriptContent) {
}
