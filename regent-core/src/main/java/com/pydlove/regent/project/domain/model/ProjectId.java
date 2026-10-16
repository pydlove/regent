package com.pydlove.regent.project.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

/**
 * 项目 ID。
 *
 * <p>强类型 ID 的意义在跨上下文引用时才显现：{@code execution} 上下文持有
 * {@code ProjectId}，编译器就能挡住「把 TaskId 传成 projectId」这类参数错位。
 */
public record ProjectId(long value) {

    public ProjectId {
        if (value <= 0) {
            throw new DomainException("项目 ID 必须为正数：" + value);
        }
    }

    public static ProjectId of(long value) {
        return new ProjectId(value);
    }

    @Override
    public String toString() {
        return Long.toString(value);
    }
}
