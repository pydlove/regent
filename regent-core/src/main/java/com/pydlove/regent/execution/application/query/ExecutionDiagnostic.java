package com.pydlove.regent.execution.application.query;

import com.pydlove.regent.execution.domain.model.RunStatus;
import com.pydlove.regent.execution.domain.model.TaskRunId;

import java.time.Instant;

/**
 * 一次执行的可操作诊断。
 *
 * <p>业务状态回答“现在是什么状态”，诊断回答“为什么停下、做到哪、下一步能做什么”。
 * 这些字段由执行记录与事件流推导，前端不得按关键词自行猜测。
 */
public record ExecutionDiagnostic(
        TaskRunId runId,
        RunStatus runStatus,
        String healthStatus,
        String failureCategory,
        String lastSuccessStage,
        String failureStage,
        boolean retryable,
        boolean requiresDeviceChange,
        boolean requiresDecision,
        String summary,
        Instant lastActivityAt) {
}
