package com.pydlove.regent.plan.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

/** {@code plan_run} 表数据对象。 */
@Data
public class PlanRunData {

    private Long id;
    private Long planId;
    private String planName;
    private String triggerType;
    private String status;
    private Integer taskCount;
    private Integer progressCount;
    private Integer succeededCount;
    private Integer failedCount;
    private Integer cancelledCount;
    private Integer skippedCount;
    private Integer runningCount;
    private Integer blockedCount;
    private String summary;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
}
