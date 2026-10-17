package com.pydlove.regent.plan.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

/** {@code plan_run_item} 表数据对象。 */
@Data
public class PlanRunItemData {

    private Long planRunId;
    private Long taskId;
    private String taskTitle;
    private String status;
    private String detail;
    private LocalDateTime updatedAt;
}
