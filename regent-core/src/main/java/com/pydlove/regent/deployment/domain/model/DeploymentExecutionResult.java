package com.pydlove.regent.deployment.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

import java.util.Objects;

/** 部署进程的终局结果。完整日志由日志存储负责，不塞进这个值对象。 */
public record DeploymentExecutionResult(
        DeploymentRunStatus status,
        Integer exitCode,
        String summary) {

    public DeploymentExecutionResult {
        Objects.requireNonNull(status, "部署执行状态不能为空");
        if (!status.isFinished()) {
            throw new DomainException("部署结果必须是终态：" + status);
        }
    }
}
