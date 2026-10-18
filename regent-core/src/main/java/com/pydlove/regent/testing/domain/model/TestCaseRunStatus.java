package com.pydlove.regent.testing.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

import java.util.Locale;

/** 测试执行状态。QUEUED 表示任务已完成、测试正在等待测试工作线程接手。 */
public enum TestCaseRunStatus {

    QUEUED,
    RUNNING,
    SUCCESS,
    FAILED,
    TIMEOUT,
    INTERRUPTED,
    SKIPPED;

    public boolean isFinished() {
        return this != QUEUED && this != RUNNING;
    }

    public boolean isFailure() {
        return this == FAILED || this == TIMEOUT || this == INTERRUPTED;
    }

    public static TestCaseRunStatus parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new DomainException("测试执行状态不能为空");
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new DomainException("无法识别的测试执行状态：" + raw);
        }
    }
}
