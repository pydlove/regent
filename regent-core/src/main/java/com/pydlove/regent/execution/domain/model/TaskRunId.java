package com.pydlove.regent.execution.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

/**
 * 执行记录 ID。
 *
 * <p>它比 {@code TaskId} 更细一层：任务问「做到哪一步了」，执行记录问「第几次尝试、
 * 那次到底发生了什么」。两者的 ID 都是裸 {@code Long} 时，日志面板里点开详情
 * 传错参数编译期不会有任何提示。
 */
public record TaskRunId(long value) {

    public TaskRunId {
        if (value <= 0) {
            throw new DomainException("执行记录 ID 必须为正数：" + value);
        }
    }

    public static TaskRunId of(long value) {
        return new TaskRunId(value);
    }

    @Override
    public String toString() {
        return Long.toString(value);
    }
}
