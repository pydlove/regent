package com.pydlove.regent.execution.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

import java.util.Locale;

/**
 * 任务状态。取值与 {@code task.status} 列、前端 {@code TaskStatus} 类型逐字对齐 —— 三方一致
 * 才能避免在每个边界做翻译，翻译层正是「前端显示的和后端存的不是一回事」的温床。
 *
 * <p>合法迁移见 {@link Task}，不要在本枚举里写判断逻辑：状态机属于聚合，
 * 枚举只回答「是不是终态」这种不依赖上下文的事实。
 */
public enum TaskStatus {

    PENDING,
    RUNNING,
    BLOCKED,
    DONE,
    FAILED,
    CANCELLED;

    /** 终态：进到这里就没有出路。FAILED 不算终态，它可以被人工重试拉回 PENDING。 */
    public boolean isTerminal() {
        return this == DONE || this == CANCELLED;
    }

    /**
     * 解析状态文本。大小写宽容，但拼错的取值一律拒绝 —— 与决策分级同一策略：
     * 把 {@code FAILED} 打成 {@code FAIL} 时若当成「不筛选」，用户会看到全量列表
     * 却以为在看失败任务，这种错误比报错难查得多。
     */
    public static TaskStatus parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new DomainException("任务状态不能为空");
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new DomainException("无法识别的任务状态：" + raw);
        }
    }
}
