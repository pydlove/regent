package com.pydlove.regent.testing.interfaces.dto;

import com.pydlove.regent.testing.domain.model.TestCaseRun;

import java.time.Instant;

/** 测试执行记录列表项，不返回大段输出。 */
public record TestCaseRunSummaryResponse(
        long id,
        long projectId,
        Long taskId,
        String trigger,
        long testCaseId,
        String testCaseName,
        String type,
        String status,
        Integer exitCode,
        String summary,
        Instant queuedAt,
        Instant startedAt,
        Instant finishedAt) {

    public static TestCaseRunSummaryResponse from(TestCaseRun run) {
        return new TestCaseRunSummaryResponse(
                run.getId().value(),
                run.getProjectId().value(),
                run.getTaskId().map(taskId -> taskId.value()).orElse(null),
                run.getTrigger().name(),
                run.getTestCaseId().value(),
                run.getTestCaseName(),
                run.getType().name(),
                run.getStatus().name(),
                run.getExitCode(),
                run.getSummary(),
                run.getQueuedAt(),
                run.getStartedAt(),
                run.getFinishedAt());
    }
}
