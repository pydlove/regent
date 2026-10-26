package com.pydlove.regent.execution.application.command;

import com.pydlove.regent.shared.domain.DomainException;

import java.time.Instant;

/** 设备代理上报的心跳快照。 */
public record DeviceHeartbeat(
        String deviceId,
        String hostname,
        String platform,
        String executor,
        String agentInstanceId,
        CodexStatus codex,
        String clientRelease,
        String shellRelease,
        Instant observedAt) {

    public DeviceHeartbeat {
        deviceId = requireText(deviceId, "设备 ID");
        hostname = requireText(hostname, "设备名称");
        platform = requireText(platform, "设备平台");
        executor = requireText(executor, "执行者");
        if (codex == null) {
            throw new DomainException("Codex 状态不能为空");
        }
        observedAt = observedAt == null ? Instant.now() : observedAt;
    }

    public record CodexStatus(
            boolean available,
            String executablePath,
            String version,
            String message,
            Instant checkedAt) {
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new DomainException(field + "不能为空");
        }
        return value.trim();
    }
}
