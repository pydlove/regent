package com.pydlove.regent.deployment.domain.port;

/** 一次部署的日志写入器。关闭后不再接收输出。 */
public interface DeploymentLogWriter extends DeploymentLogSink {

    void close();
}
