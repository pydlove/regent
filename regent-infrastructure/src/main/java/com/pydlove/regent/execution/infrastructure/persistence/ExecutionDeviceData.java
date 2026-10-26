package com.pydlove.regent.execution.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

/** {@code execution_device} 的数据对象。 */
@Data
public class ExecutionDeviceData {

    private Long id;
    private String deviceId;
    private Long ownerId;
    private String hostname;
    private String platform;
    private String executor;
    private String agentInstanceId;
    private Boolean codexAvailable;
    private String codexExecutablePath;
    private String codexVersion;
    private String codexMessage;
    private LocalDateTime codexCheckedAt;
    private String clientRelease;
    private String shellRelease;
    private LocalDateTime lastSeenAt;
    private LocalDateTime lastClaimedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
