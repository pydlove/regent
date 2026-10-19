package com.pydlove.regent.execution.interfaces.dto;

import com.pydlove.regent.execution.domain.model.ExecutorRuntimeStatus;

import java.time.Instant;

/** 设置页的 Codex 运行时检测结果。 */
public record CodexRuntimeResponse(
        boolean available,
        String configuredCommand,
        String executablePath,
        String version,
        String message,
        Instant checkedAt) {

    public static CodexRuntimeResponse from(ExecutorRuntimeStatus status) {
        return new CodexRuntimeResponse(
                status.available(),
                status.configuredCommand(),
                status.executablePath(),
                status.version(),
                status.message(),
                status.checkedAt());
    }
}
