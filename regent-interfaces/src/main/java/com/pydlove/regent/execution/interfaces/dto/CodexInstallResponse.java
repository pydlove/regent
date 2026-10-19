package com.pydlove.regent.execution.interfaces.dto;

import com.pydlove.regent.execution.domain.model.ExecutorInstallResult;

/** 安装结果与本轮输出；前端据此显示失败原因和最新可用状态。 */
public record CodexInstallResponse(
        boolean success,
        String method,
        String message,
        String output,
        CodexRuntimeResponse runtime) {

    public static CodexInstallResponse from(ExecutorInstallResult result) {
        return new CodexInstallResponse(
                result.success(),
                result.method().name(),
                result.message(),
                result.output(),
                CodexRuntimeResponse.from(result.runtime()));
    }
}
