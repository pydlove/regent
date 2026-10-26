package com.pydlove.regent.execution.interfaces.dto;

import com.pydlove.regent.execution.application.command.DeviceHeartbeat;

import java.time.Instant;
import java.time.format.DateTimeParseException;

/** 设备代理上报的当前设备状态。 */
public record DevicePresenceRequest(
        String deviceId,
        String hostname,
        String platform,
        String executor,
        String agentInstanceId,
        Codex codex,
        String clientRelease,
        String shellRelease) {

    public DevicePresenceRequest(String deviceId,
                                 String hostname,
                                 String platform,
                                 String executor,
                                 Codex codex) {
        this(deviceId, hostname, platform, executor, null, codex, null, null);
    }

    public DeviceHeartbeat toHeartbeat(Instant observedAt) {
        Codex value = codex == null
                ? new Codex(false, null, null, "设备没有上报 Codex 状态", null)
                : codex;
        return new DeviceHeartbeat(
                deviceId,
                hostname,
                platform,
                executor,
                agentInstanceId,
                new DeviceHeartbeat.CodexStatus(
                        value.available(),
                        value.executablePath(),
                        value.version(),
                        value.message(),
                        parseInstant(value.checkedAt(), observedAt)),
                clientRelease,
                shellRelease,
                observedAt);
    }

    private static Instant parseInstant(String value, Instant fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            return fallback;
        }
    }

    public record Codex(
            boolean available,
            String executablePath,
            String version,
            String message,
            String checkedAt) {
    }
}
