package com.pydlove.regent.deployment.domain.port;

import com.pydlove.regent.deployment.domain.model.DeploymentExecutionRequest;
import com.pydlove.regent.deployment.domain.model.DeploymentExecutionResult;

/** 远程部署执行器端口。实现可以是 SSH、云厂商 API 或本机脚本。 */
public interface DeploymentExecutor {

    DeploymentExecutionResult execute(DeploymentExecutionRequest request, DeploymentLogSink sink);
}
