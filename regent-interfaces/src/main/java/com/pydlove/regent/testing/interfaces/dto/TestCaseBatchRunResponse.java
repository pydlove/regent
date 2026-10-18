package com.pydlove.regent.testing.interfaces.dto;

import com.pydlove.regent.testing.application.command.TestCaseManualRunResult;

import java.util.List;

/** 批量手动执行结果：成功入队的记录与逐项失败原因。 */
public record TestCaseBatchRunResponse(
        List<TestCaseRunSummaryResponse> runs,
        List<Failure> failures) {

    public static TestCaseBatchRunResponse from(TestCaseManualRunResult result) {
        return new TestCaseBatchRunResponse(
                result.runs().stream().map(TestCaseRunSummaryResponse::from).toList(),
                result.failures().stream()
                        .map(failure -> new Failure(
                                failure.testCaseId().value(), failure.message()))
                        .toList());
    }

    public record Failure(long testCaseId, String message) {
    }
}
