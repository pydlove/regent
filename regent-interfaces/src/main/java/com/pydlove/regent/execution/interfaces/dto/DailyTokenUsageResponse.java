package com.pydlove.regent.execution.interfaces.dto;

import com.pydlove.regent.execution.domain.repository.DailyTokenUsage;

import java.time.LocalDate;

/** 某一天的 Token 消耗明细。 */
public record DailyTokenUsageResponse(
        LocalDate date,
        long input,
        long cachedInput,
        long output,
        long total,
        long runCount) {

    public static DailyTokenUsageResponse from(DailyTokenUsage usage) {
        return new DailyTokenUsageResponse(
                usage.day(),
                usage.usage().input(),
                usage.usage().cachedInput(),
                usage.usage().output(),
                usage.usage().total(),
                usage.runCount());
    }
}
