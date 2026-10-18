package com.pydlove.regent.testing.interfaces.dto;

import com.pydlove.regent.testing.domain.model.TestCaseRun;

import java.time.Instant;

/** 一次测试执行的完整记录。 */
public record TestCaseRunDetailResponse(
        long id,
        long projectId,
        Long taskId,
        String trigger,
        long testCaseId,
        String testCaseName,
        String type,
        String command,
        String scriptFilename,
        String scriptHash,
        String status,
        Integer exitCode,
        String summary,
        String output,
        Instant queuedAt,
        Instant startedAt,
        Instant finishedAt) {

    public static TestCaseRunDetailResponse from(TestCaseRun run) {
        return new TestCaseRunDetailResponse(
                run.getId().value(),
                run.getProjectId().value(),
                run.getTaskId().map(taskId -> taskId.value()).orElse(null),
                run.getTrigger().name(),
                run.getTestCaseId().value(),
                run.getTestCaseName(),
                run.getType().name(),
                run.getCommand(),
                run.getScriptFilename(),
                run.getScriptHash(),
                run.getStatus().name(),
                run.getExitCode(),
                run.getSummary(),
                run.getOutput(),
                run.getQueuedAt(),
                run.getStartedAt(),
                run.getFinishedAt());
    }
}
