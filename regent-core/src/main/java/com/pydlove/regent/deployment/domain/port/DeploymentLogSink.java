package com.pydlove.regent.deployment.domain.port;

/** 部署进程输出接收端。实现负责落盘，领域层不关心文件路径。 */
@FunctionalInterface
public interface DeploymentLogSink {

    void append(String text);
}
