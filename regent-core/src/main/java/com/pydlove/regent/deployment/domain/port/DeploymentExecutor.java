package com.pydlove.regent.deployment.domain.port;

import com.pydlove.regent.deployment.domain.model.DeploymentExecutionRequest;
import com.pydlove.regent.deployment.domain.model.DeploymentExecutionResult;
import com.pydlove.regent.deployment.domain.model.DeploymentRunId;

/** 远程部署执行器端口。实现可以是 SSH、云厂商 API 或本机脚本。 */
public interface DeploymentExecutor {

    DeploymentExecutionResult execute(DeploymentExecutionRequest request, DeploymentLogSink sink);

    /**
     * 请求终止正在运行的部署进程。
     *
     * @return true 表示执行器找到并接受了停止请求；false 表示进程尚未启动、已经结束或执行器不支持
     */
    default boolean cancel(DeploymentRunId runId) {
        return false;
    }
}
