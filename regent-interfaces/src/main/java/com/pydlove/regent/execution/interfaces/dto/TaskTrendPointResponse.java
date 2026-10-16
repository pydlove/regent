package com.pydlove.regent.execution.interfaces.dto;

import com.pydlove.regent.execution.domain.repository.DailyCompletionCount;

/** 看板趋势柱状图的一天，{@code date} 为 {@code yyyy-MM-dd}。 */
public record TaskTrendPointResponse(String date, long done, long failed) {

    public static TaskTrendPointResponse from(DailyCompletionCount count) {
        return new TaskTrendPointResponse(count.day().toString(), count.done(), count.failed());
    }
}
