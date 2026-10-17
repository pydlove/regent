package com.pydlove.regent.plan.interfaces.dto;

import java.time.Instant;
import java.util.List;

/** 创建计划的 HTTP 入参。 */
public record PlanCreateRequest(
        String name,
        List<Long> taskIds,
        Instant scheduledAt) {
}
