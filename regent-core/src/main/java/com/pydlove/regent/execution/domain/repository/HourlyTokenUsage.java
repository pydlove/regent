package com.pydlove.regent.execution.domain.repository;

import com.pydlove.regent.shared.kernel.TokenUsage;

import java.time.LocalDateTime;
import java.util.Objects;

/** 某一个整点小时内已结算执行的 Token 消耗。时间已按用户时区切桶。 */
public record HourlyTokenUsage(LocalDateTime bucketStart, TokenUsage usage, long runCount) {

    public HourlyTokenUsage {
        Objects.requireNonNull(bucketStart, "小时起点不能为空");
        usage = usage == null ? TokenUsage.ZERO : usage;
        if (runCount < 0) {
            throw new IllegalArgumentException("执行次数不能为负：" + runCount);
        }
    }
}
