package com.pydlove.regent.execution.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

/** 按整点小时聚合的 Token 查询行。 */
@Data
public class HourlyTokenUsageRow {

    private LocalDateTime bucketStart;
    private Long tokenInput;
    private Long tokenCachedInput;
    private Long tokenOutput;
    private Long runCount;
}
