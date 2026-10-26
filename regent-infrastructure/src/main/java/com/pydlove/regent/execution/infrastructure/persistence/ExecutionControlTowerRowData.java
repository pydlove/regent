package com.pydlove.regent.execution.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

/** 执行控制塔联合查询的数据对象。 */
@Data
public class ExecutionControlTowerRowData {

    private String executionType;
    private Long businessId;
    private Long runId;
    private Integer attemptNo;
    private Long projectId;
    private String projectName;
    private String name;
    private String businessStatus;
    private String runStatus;
    private String executor;
    private String agentName;
    private Integer exitCode;
    private String summary;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime updatedAt;
    private LocalDateTime leaseExpiresAt;
    private LocalDateTime lastEventAt;
}
