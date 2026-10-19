package com.pydlove.regent.execution.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

import java.time.Instant;

/**
 * 一次执行器可用性检测的结果。
 *
 * <p>这里只描述运行时事实，不绑定具体执行器；Codex 的命令解析与版本探测发生在基础设施适配器中。
 * {@code available=false} 不是异常：未安装、不可执行、版本命令失败都是需要在设置页展示的状态。
 */
public record ExecutorRuntimeStatus(
        boolean available,
        String configuredCommand,
        String executablePath,
        String version,
        String message,
        Instant checkedAt) {

    public ExecutorRuntimeStatus {
        if (configuredCommand == null || configuredCommand.isBlank()) {
            throw new DomainException("执行器配置命令不能为空");
        }
        configuredCommand = configuredCommand.trim();
        executablePath = blankToNull(executablePath);
        version = blankToNull(version);
        message = message == null || message.isBlank() ? "执行器状态未知" : message.trim();
        if (checkedAt == null) {
            throw new DomainException("执行器检测时间不能为空");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
