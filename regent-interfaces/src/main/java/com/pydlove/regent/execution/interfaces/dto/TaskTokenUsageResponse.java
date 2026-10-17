package com.pydlove.regent.execution.interfaces.dto;

import com.pydlove.regent.shared.kernel.TokenUsage;

/** 看板展示的全平台 token 汇总。 */
public record TaskTokenUsageResponse(
        long input,
        long cachedInput,
        long output,
        long total
) {

    public static TaskTokenUsageResponse from(TokenUsage usage) {
        return new TaskTokenUsageResponse(
                usage.input(),
                usage.cachedInput(),
                usage.output(),
                usage.total());
    }
}
