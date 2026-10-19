package com.pydlove.regent.deployment.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

import java.util.Locale;

/** 一次部署尝试的状态。QUEUED 表示记录已落库、工作线程尚未接手。 */
public enum DeploymentRunStatus {

    QUEUED,
    RUNNING,
    SUCCESS,
    FAILED,
    TIMEOUT,
    INTERRUPTED;

    public boolean isFinished() {
        return this != QUEUED && this != RUNNING;
    }

    public boolean isActive() {
        return !isFinished();
    }

    public static DeploymentRunStatus parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new DomainException("部署执行状态不能为空");
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new DomainException("无法识别的部署执行状态：" + raw);
        }
    }
}
