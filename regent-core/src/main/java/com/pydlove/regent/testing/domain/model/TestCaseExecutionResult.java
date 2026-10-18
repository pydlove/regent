package com.pydlove.regent.testing.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

import java.util.Objects;

/** 测试进程返回的终局结果。 */
public record TestCaseExecutionResult(
        TestCaseRunStatus status,
        Integer exitCode,
        String summary,
        String output) {

    public TestCaseExecutionResult {
        Objects.requireNonNull(status, "测试执行状态不能为空");
        if (!status.isFinished()) {
            throw new DomainException("测试结果必须是终态：" + status);
        }
    }
}
