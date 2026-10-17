package com.pydlove.regent.execution.domain.repository;

import com.pydlove.regent.execution.domain.model.TaskStatus;
import com.pydlove.regent.shared.kernel.TokenUsage;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/**
 * 任务查询仓储（只读）。
 *
 * <p>与 {@link TaskRepository} 的分工：这里回答「列表要显示什么」，那里回答「任务能做什么」。
 */
public interface TaskQueryRepository {

    TaskListItemPage findPage(TaskQuery query);

    /** 状态分布。缺失的状态不出现在 Map 里，由应用服务补零。 */
    Map<TaskStatus, Long> countGroupByStatus();

    /** 全部有效任务的已结算 token 汇总。未产生执行记录时返回 {@link TokenUsage#ZERO}。 */
    TokenUsage summarizeTokens();

    /**
     * 按自然日统计收场任务（DONE / FAILED），返回的 {@code day} 已在 {@code offset} 时区里。
     *
     * <p>切天必须由 SQL 完成：时间列按约定存 UTC 字面量，拉回内存再分组等于把整张任务表
     * 拖进 JVM。没有数据的日子不返回，补零是应用层的事。
     */
    List<DailyCompletionCount> countCompletionPerDay(LocalDate from, LocalDate to, ZoneOffset offset);
}
