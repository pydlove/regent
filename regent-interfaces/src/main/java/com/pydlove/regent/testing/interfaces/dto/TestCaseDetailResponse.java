package com.pydlove.regent.testing.interfaces.dto;

import com.pydlove.regent.testing.application.query.TestCaseOverview;

import java.time.Instant;

/** 测试用例详情，额外返回可编辑的脚本正文。 */
public record TestCaseDetailResponse(
        long id,
        long projectId,
        String projectName,
        String name,
        String type,
        String description,
        String command,
        String scriptFilename,
        String scriptContent,
        String scriptHash,
        boolean enabled,
        String latestRunStatus,
        Instant latestRunAt,
        Instant createdAt,
        Instant updatedAt) {

    public static TestCaseDetailResponse from(TestCaseOverview overview) {
        var testCase = overview.testCase();
        var latest = overview.latestRun();
        return new TestCaseDetailResponse(
                testCase.getId().value(),
                testCase.getProjectId().value(),
                overview.projectName(),
                testCase.getName(),
                testCase.getType().name(),
                testCase.getDescription(),
                testCase.getCommand(),
                testCase.getScriptFilename(),
                testCase.getScriptContent(),
                testCase.getScriptHash(),
                testCase.isEnabled(),
                latest == null ? null : latest.getStatus().name(),
                latest == null ? null : latest.getFinishedAt() != null
                        ? latest.getFinishedAt() : latest.getQueuedAt(),
                testCase.getCreatedAt(),
                testCase.getUpdatedAt());
    }
}
