package com.pydlove.regent.execution.infrastructure.persistence;

import com.pydlove.regent.execution.domain.model.ExecutionDevice;
import com.pydlove.regent.identity.domain.model.UserId;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** 执行设备心跳的数据映射。 */
public final class ExecutionDeviceDataMapper {

    private ExecutionDeviceDataMapper() {
    }

    public static ExecutionDeviceData toData(ExecutionDevice device) {
        ExecutionDeviceData data = new ExecutionDeviceData();
        data.setDeviceId(device.deviceId());
        data.setOwnerId(device.ownerId().value());
        data.setHostname(device.hostname());
        data.setPlatform(device.platform());
        data.setExecutor(device.executor());
        data.setAgentInstanceId(device.agentInstanceId());
        data.setCodexAvailable(device.codex().available());
        data.setCodexExecutablePath(device.codex().executablePath());
        data.setCodexVersion(device.codex().version());
        data.setCodexMessage(device.codex().message());
        data.setCodexCheckedAt(toDb(device.codex().checkedAt()));
        data.setClientRelease(device.clientRelease());
        data.setShellRelease(device.shellRelease());
        data.setLastSeenAt(toDb(device.lastSeenAt()));
        data.setLastClaimedAt(toDb(device.lastClaimedAt()));
        data.setCreatedAt(toDb(device.createdAt()));
        data.setUpdatedAt(toDb(device.updatedAt()));
        return data;
    }

    public static ExecutionDevice toDomain(ExecutionDeviceData data) {
        return new ExecutionDevice(
                UserId.of(data.getOwnerId()),
                data.getDeviceId(),
                data.getHostname(),
                data.getPlatform(),
                data.getExecutor(),
                data.getAgentInstanceId(),
                new ExecutionDevice.CodexStatus(
                        Boolean.TRUE.equals(data.getCodexAvailable()),
                        data.getCodexExecutablePath(),
                        data.getCodexVersion(),
                        data.getCodexMessage(),
                        fromDb(data.getCodexCheckedAt())),
                data.getClientRelease(),
                data.getShellRelease(),
                fromDb(data.getLastSeenAt()),
                fromDb(data.getLastClaimedAt()),
                fromDb(data.getCreatedAt()),
                fromDb(data.getUpdatedAt()));
    }

    private static LocalDateTime toDb(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant fromDb(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
