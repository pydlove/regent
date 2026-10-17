package com.pydlove.regent.execution.infrastructure.persistence;

import lombok.Data;

/** 全平台 token 汇总行；三个分量来自 {@code task_run}。 */
@Data
public class TokenUsageRow {

    private Long tokenInput;
    private Long tokenCachedInput;
    private Long tokenOutput;
}
