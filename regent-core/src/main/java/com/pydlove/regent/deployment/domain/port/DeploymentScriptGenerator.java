package com.pydlove.regent.deployment.domain.port;

import com.pydlove.regent.deployment.domain.model.DeploymentScriptGenerationRequest;

/** 根据项目工作区与目标服务器信息生成远程部署脚本。 */
public interface DeploymentScriptGenerator {

    String generate(DeploymentScriptGenerationRequest request);
}
