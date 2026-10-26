package com.pydlove.regent.execution.domain.model;

import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.shared.domain.DomainException;

import java.time.Duration;
import java.time.Instant;

/**
 * 执行设备的一次持久化心跳。
 *
 * <p>设备是否在线由“最后一次心跳 + TTL”推导，不作为可写的业务状态保存。这样重启后不会留下
 * 一个永远在线的内存标记，也不会出现“状态字段说在线、时间戳说半小时没心跳”的双重真相。
 */
public record ExecutionDevice(
        UserId ownerId,
        String deviceId,
        String hostname,
        String platform,
        String executor,
        String agentInstanceId,
        CodexStatus codex,
        String clientRelease,
        String shellRelease,
        Instant lastSeenAt,
        Instant lastClaimedAt,
        Instant createdAt,
        Instant updatedAt) {

    public ExecutionDevice {
        ownerId = requireNotNull(ownerId, "设备归属账号");
        deviceId = requireText(deviceId, "设备 ID");
        hostname = requireText(hostname, "设备名称");
        platform = requireText(platform, "设备平台");
        executor = requireText(executor, "执行者");
        agentInstanceId = blankToNull(agentInstanceId);
        codex = requireNotNull(codex, "Codex 状态");
        clientRelease = blankToNull(clientRelease);
        shellRelease = blankToNull(shellRelease);
        lastSeenAt = requireNotNull(lastSeenAt, "最后心跳时间");
        createdAt = requireNotNull(createdAt, "创建时间");
        updatedAt = requireNotNull(updatedAt, "更新时间");
    }

    public static ExecutionDevice heartbeat(ExecutionDevice existing,
                                            UserId ownerId,
                                            String deviceId,
                                            String hostname,
                                            String platform,
                                            String executor,
                                            String agentInstanceId,
                                            CodexStatus codex,
                                            String clientRelease,
                                            String shellRelease,
                                            Instant observedAt) {
        Instant now = requireNotNull(observedAt, "最后心跳时间");
        return new ExecutionDevice(
                ownerId,
                deviceId,
                hostname,
                platform,
                executor,
                agentInstanceId,
                codex,
                clientRelease,
                shellRelease,
                now,
                existing == null ? null : existing.lastClaimedAt(),
                existing == null ? now : existing.createdAt(),
                now);
    }

    public ExecutionDevice markClaimed(Instant claimedAt) {
        Instant now = requireNotNull(claimedAt, "领取任务时间");
        return new ExecutionDevice(
                ownerId, deviceId, hostname, platform, executor, agentInstanceId, codex,
                clientRelease, shellRelease, lastSeenAt, now, createdAt, now);
    }

    public boolean isOnline(Instant now, Duration onlineTtl) {
        return !lastSeenAt.plus(onlineTtl).isBefore(now);
    }

    public record CodexStatus(
            boolean available,
            String executablePath,
            String version,
            String message,
            Instant checkedAt) {

        public CodexStatus {
            executablePath = blankToNull(executablePath);
            version = blankToNull(version);
            message = message == null || message.isBlank() ? "Codex 状态未知" : message.trim();
            checkedAt = requireNotNull(checkedAt, "Codex 检测时间");
        }
    }

    private static <T> T requireNotNull(T value, String field) {
        if (value == null) {
            throw new DomainException(field + "不能为空");
        }
        return value;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new DomainException(field + "不能为空");
        }
        return value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
