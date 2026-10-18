package com.pydlove.regent.testing.interfaces.dto;

import com.pydlove.regent.testing.application.query.TestCaseOverview;
import com.pydlove.regent.testing.domain.model.TestCase;
import com.pydlove.regent.testing.domain.model.TestCaseRun;

import java.time.Instant;

/** 测试用例列表项。 */
public record TestCaseSummaryResponse(
        long id,
        long projectId,
        String projectName,
        String name,
        String type,
        String description,
        String command,
        String scriptFilename,
        String scriptHash,
        int scriptSize,
        boolean enabled,
        String latestRunStatus,
        Instant latestRunAt,
        Instant createdAt,
        Instant updatedAt) {

    public static TestCaseSummaryResponse from(TestCaseOverview overview) {
        TestCase testCase = overview.testCase();
        TestCaseRun latest = overview.latestRun();
        return new TestCaseSummaryResponse(
                testCase.getId().value(),
                testCase.getProjectId().value(),
                overview.projectName(),
                testCase.getName(),
                testCase.getType().name(),
                testCase.getDescription(),
                testCase.getCommand(),
                testCase.getScriptFilename(),
                testCase.getScriptHash(),
                testCase.getScriptContent() == null ? 0 : testCase.getScriptContent().length(),
                testCase.isEnabled(),
                latest == null ? null : latest.getStatus().name(),
                latest == null ? null : latest.getFinishedAt() != null
                        ? latest.getFinishedAt() : latest.getQueuedAt(),
                testCase.getCreatedAt(),
                testCase.getUpdatedAt());
    }
}
