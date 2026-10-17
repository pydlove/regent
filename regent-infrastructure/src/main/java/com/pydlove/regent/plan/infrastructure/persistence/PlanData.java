package com.pydlove.regent.plan.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

/** {@code plan} 表数据对象。 */
@Data
public class PlanData {

    private Long id;
    private String name;
    private String status;
    private LocalDateTime scheduledAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
