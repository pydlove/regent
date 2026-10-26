package com.pydlove.regent.execution.interfaces.dto;

import com.pydlove.regent.execution.application.command.DevicePresence;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;

/** 浏览器读取的当前设备状态，以及代理本轮需要领取的任务。 */
public record DevicePresenceResponse(
        String deviceId,
        String hostname,
        String platform,
        String executor,
        String agentInstanceId,
        boolean online,
        DevicePresenceRequest.Codex codex,
        String clientRelease,
        String shellRelease,
        Instant lastSeenAt,
        List<Long> taskIds) {

    public DevicePresenceResponse {
        taskIds = taskIds == null ? List.of() : List.copyOf(taskIds);
    }

    public static DevicePresenceResponse from(DevicePresence presence) {
        var device = presence.device();
        return new DevicePresenceResponse(
                device.deviceId(),
                device.hostname(),
                device.platform(),
                device.executor(),
                device.agentInstanceId(),
                presence.online(),
                new DevicePresenceRequest.Codex(
                        device.codex().available(),
                        device.codex().executablePath(),
                        device.codex().version(),
                        device.codex().message(),
                        device.codex().checkedAt().toString()),
                device.clientRelease(),
                device.shellRelease(),
                device.lastSeenAt(),
                presence.taskIds());
    }

    public DevicePresenceResponse withoutTasks() {
        return new DevicePresenceResponse(
                deviceId, hostname, platform, executor, agentInstanceId, online, codex,
                clientRelease, shellRelease, lastSeenAt, List.of());
    }

    public DevicePresenceResponse withAdditionalTasks(List<Long> additionalTaskIds) {
        LinkedHashSet<Long> merged = new LinkedHashSet<>(taskIds);
        if (additionalTaskIds != null) {
            merged.addAll(additionalTaskIds);
        }
        return new DevicePresenceResponse(
                deviceId, hostname, platform, executor, agentInstanceId, online, codex,
                clientRelease, shellRelease, lastSeenAt,
                List.copyOf(merged));
    }
}
