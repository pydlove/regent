package com.pydlove.regent.execution.domain.repository;

import com.pydlove.regent.shared.kernel.TokenUsage;

import java.time.LocalDate;
import java.util.Objects;

/** 某一个自然日内已结算执行的 Token 消耗。 */
public record DailyTokenUsage(LocalDate day, TokenUsage usage, long runCount) {

    public DailyTokenUsage {
        Objects.requireNonNull(day, "日期不能为空");
        usage = usage == null ? TokenUsage.ZERO : usage;
        if (runCount < 0) {
            throw new IllegalArgumentException("执行次数不能为负：" + runCount);
        }
    }
}
