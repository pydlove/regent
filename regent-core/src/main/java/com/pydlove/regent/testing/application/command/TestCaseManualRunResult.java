package com.pydlove.regent.testing.application.command;

import com.pydlove.regent.testing.domain.model.TestCaseId;
import com.pydlove.regent.testing.domain.model.TestCaseRun;

import java.util.List;

/** 手动执行一批测试的结果：成功的已入队记录，以及未能入队的逐项原因。 */
public record TestCaseManualRunResult(List<TestCaseRun> runs, List<Failure> failures) {

    public TestCaseManualRunResult {
        runs = List.copyOf(runs);
        failures = List.copyOf(failures);
    }

    public record Failure(TestCaseId testCaseId, String message) {
    }
}
