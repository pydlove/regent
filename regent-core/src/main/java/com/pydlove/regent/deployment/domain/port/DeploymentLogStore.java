package com.pydlove.regent.deployment.domain.port;

import com.pydlove.regent.deployment.domain.model.DeploymentRunId;

/** 部署日志存储端口。日志属于运行时产物，不写仓库。 */
public interface DeploymentLogStore {

    DeploymentLogWriter open(DeploymentRunId runId);

    String read(DeploymentRunId runId);

    void delete(DeploymentRunId runId);
}
