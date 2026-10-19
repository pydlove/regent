package com.pydlove.regent.execution.interfaces.dto;

import com.pydlove.regent.execution.domain.repository.HourlyTokenUsage;

import java.time.LocalDateTime;

/** 某一个整点小时的 Token 消耗。 */
public record HourlyTokenUsageResponse(
        LocalDateTime bucketStart,
        long input,
        long cachedInput,
        long output,
        long total,
        long runCount) {

    public static HourlyTokenUsageResponse from(HourlyTokenUsage usage) {
        return new HourlyTokenUsageResponse(
                usage.bucketStart(),
                usage.usage().input(),
                usage.usage().cachedInput(),
                usage.usage().output(),
                usage.usage().total(),
                usage.runCount());
    }
}
