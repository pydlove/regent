package com.pydlove.regent.execution.domain.model;

/**
 * 任务的来源。决定任务携带哪个上游引用：
 * {@code REQUIREMENT} 必带需求，{@code BUG_FIX} 必带缺陷单，{@code MANUAL} 两者都没有。
 */
public enum TaskSource {
    REQUIREMENT,
    BUG_FIX,
    MANUAL
}
