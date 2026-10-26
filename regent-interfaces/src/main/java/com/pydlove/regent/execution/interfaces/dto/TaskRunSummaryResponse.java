package com.pydlove.regent.execution.interfaces.dto;

import com.pydlove.regent.execution.domain.model.TaskRun;

import java.time.Instant;
import java.util.List;

/**
 * 执行记录列表行。token 三个分量分开给，是因为它们的价钱差一个数量级 ——
 * 只给总数，界面上就没法回答「这次为什么这么贵」。
 *
 * <p>{@code executor} / {@code agentName} / {@code leaseExpiresAt} 回答的是另一个问题：
 * 「这次是谁在跑」。界面必须能区分「平台自己拉起的 codex」与「外部执行者」——
 * 外部执行者跑在别的进程里，它中途消失时看到的是一条租约停在那里的记录，
 * 而不是一条永远不动的「执行中」。
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
        String executor,
        String agentName,
        String deviceId,
        Instant leaseExpiresAt,
        Instant lastHeartbeatAt,
        Instant lastEventAt,
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
                run.getExecutor(),
                run.getAgentName(),
                run.getDeviceId(),
                run.getLeaseExpiresAt(),
                run.getLastHeartbeatAt(),
                run.getLastEventAt(),
                run.getSummary(),
                run.isSchemaValid(),
                run.getChangedFiles(),
                run.getStartedAt(),
                run.getFinishedAt());
    }
}
