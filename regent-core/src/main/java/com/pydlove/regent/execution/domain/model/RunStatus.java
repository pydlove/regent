package com.pydlove.regent.execution.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

import java.util.Locale;

/**
 * 一次执行记录的状态。取值与 {@code task_run.status} 列逐字对齐。
 *
 * <p>与 {@link TaskStatus} 的关系是「一对多」：任务层面的 {@code BLOCKED} 表示等人拍板，
 * 而一次执行记录的 {@code BLOCKED} 表示「这一轮跑到一半停下来，把问题交出去了」——
 * 它是一次带结论的暂停，记录本身已经收敛，不是失败。
 *
 * <p>{@code RUNNING} 不在 {@code V1__init_schema.sql} 最初的取值注释里，是 V2 迁移补上的：
 * 事件流需要一个在进程还在跑时就存在的父行（{@code run_event.task_run_id} 是外键），
 * 而「先写结果、跑完再落行」会让运行中的日志没有任何挂靠点。
 */
public enum RunStatus {

    RUNNING,
    SUCCESS,
    FAILED,
    BLOCKED,
    TIMEOUT;

    /** 是否已收敛。收敛之后不允许再写结论。 */
    public boolean isFinished() {
        return this != RUNNING;
    }

    public boolean isFailure() {
        return this == FAILED || this == TIMEOUT;
    }

    public static RunStatus parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new DomainException("执行状态不能为空");
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new DomainException("无法识别的执行状态：" + raw);
        }
    }
}
