package com.pydlove.regent.project.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

/** 项目资料的生成状态。资料由 Codex 只读分析工作区后产出。 */
public enum ProjectSummaryStatus {
    NOT_GENERATED,
    GENERATING,
    READY,
    FAILED;

    public static ProjectSummaryStatus parse(String value) {
        if (value == null || value.isBlank()) {
            throw new DomainException("项目资料状态不能为空");
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new DomainException("未知的项目资料状态：" + value);
        }
    }
}
