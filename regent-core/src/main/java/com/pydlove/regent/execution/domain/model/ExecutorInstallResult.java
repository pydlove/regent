package com.pydlove.regent.execution.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

/** 一次一键安装的结果；安装命令输出与安装后的最新检测状态一起返回。 */
public record ExecutorInstallResult(
        boolean success,
        ExecutorInstallMethod method,
        String message,
        String output,
        ExecutorRuntimeStatus runtime) {

    public ExecutorInstallResult {
        if (method == null) {
            throw new DomainException("安装方式不能为空");
        }
        message = message == null || message.isBlank() ? "安装结果未知" : message.trim();
        output = output == null ? "" : output;
        if (runtime == null) {
            throw new DomainException("安装后的执行器状态不能为空");
        }
    }
}
