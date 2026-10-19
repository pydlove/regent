package com.pydlove.regent.execution.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDate;

/** 按自然日聚合的 Token 查询行。 */
@Data
public class DailyTokenUsageRow {

    private LocalDate day;
    private Long tokenInput;
    private Long tokenCachedInput;
    private Long tokenOutput;
    private Long runCount;
}
