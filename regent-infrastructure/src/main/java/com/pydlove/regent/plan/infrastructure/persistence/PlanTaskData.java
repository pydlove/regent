package com.pydlove.regent.plan.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

/** {@code plan_task} 表数据对象。 */
@Data
public class PlanTaskData {

    private Long planId;
    private Long taskId;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}
