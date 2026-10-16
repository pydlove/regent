package com.pydlove.regent.execution.domain.model;

import com.pydlove.regent.shared.kernel.TokenUsage;

import java.time.Instant;
import java.util.List;

/**
 * 执行记录的重建快照 —— 供仓储把数据库里的行还原成聚合。
 *
 * <p>用记录而不是一长串构造参数：字段一多，位置参数传错编译器不会吭声，
 * 而这里传错的代价是「第几次尝试」与「哪次结果」对不上号的审计错误。
 */
public record TaskRunSnapshot(
        TaskRunId id,
        TaskId taskId,
        int attemptNo,
        String branchName,
        RunStatus status,
        Integer exitCode,
        TokenUsage tokens,
        String summary,
        String rawResult,
        boolean schemaValid,
        List<String> changedFiles,
        Instant startedAt,
        Instant finishedAt,
        Instant createdAt) {
}
