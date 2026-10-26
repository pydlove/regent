package com.pydlove.regent.execution.interfaces.dto;

import com.pydlove.regent.execution.application.query.ExecutionDiagnostic;

import java.time.Instant;

/** 执行诊断的对外契约。 */
public record ExecutionDiagnosticResponse(
        String healthStatus,
        String failureCategory,
        String lastSuccessStage,
        String failureStage,
        boolean retryable,
        boolean requiresDeviceChange,
        boolean requiresDecision,
        String summary,
        Instant lastActivityAt) {

    public static ExecutionDiagnosticResponse from(ExecutionDiagnostic diagnostic) {
        return new ExecutionDiagnosticResponse(
                diagnostic.healthStatus(),
                diagnostic.failureCategory(),
                diagnostic.lastSuccessStage(),
                diagnostic.failureStage(),
                diagnostic.retryable(),
                diagnostic.requiresDeviceChange(),
                diagnostic.requiresDecision(),
                diagnostic.summary(),
                diagnostic.lastActivityAt());
    }
}
