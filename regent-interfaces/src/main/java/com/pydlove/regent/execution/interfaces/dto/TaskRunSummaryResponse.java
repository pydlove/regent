package com.pydlove.regent.execution.interfaces.dto;

import com.pydlove.regent.execution.domain.model.TaskRun;

import java.time.Instant;
import java.util.List;

/**
 * 执行记录列表行。token 三个分量分开给，是因为它们的价钱差一个数量级 ——
 * 只给总数，界面上就没法回答「这次为什么这么贵」。
 */
public record TaskRunSummaryResponse(
        long id,
        long taskId,
        int attemptNo,
        String status,
        Integer exitCode,
        long tokenInput,
        long tokenCachedInput,
        long tokenOutput,
        long tokens,
        String branchName,
        String summary,
        boolean schemaValid,
        List<String> changedFiles,
        Instant startedAt,
        Instant finishedAt) {

    public static TaskRunSummaryResponse from(TaskRun run) {
        return new TaskRunSummaryResponse(
                run.getId().value(),
                run.getTaskId().value(),
                run.getAttemptNo(),
                run.getStatus().name(),
                run.getExitCode(),
                run.getTokens().input(),
                run.getTokens().cachedInput(),
                run.getTokens().output(),
                run.getTokens().total(),
                run.getBranchName(),
                run.getSummary(),
                run.isSchemaValid(),
                run.getChangedFiles(),
                run.getStartedAt(),
                run.getFinishedAt());
    }
}
