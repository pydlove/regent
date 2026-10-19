package com.pydlove.regent.execution.domain.repository;

import com.pydlove.regent.execution.domain.model.TaskStatus;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.kernel.TokenUsage;

import java.time.Instant;
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
    Map<TaskStatus, Long> countGroupByStatus(ProjectId projectId);

    default Map<TaskStatus, Long> countGroupByStatus(ProjectId projectId, UserId ownerId) {
        return countGroupByStatus(projectId);
    }

    /** 有效任务的已结算 token 汇总；指定项目时只汇总该项目。 */
    TokenUsage summarizeTokens(ProjectId projectId);

    default TokenUsage summarizeTokens(ProjectId projectId, UserId ownerId) {
        return summarizeTokens(projectId);
    }

    /**
     * 按用户时区的自然日聚合 Token 消耗。{@code from}/{@code to} 为 UTC 瞬间，
     * 为空表示不限制边界；没有数据的日期不返回，由应用层按范围补零。
     */
    List<DailyTokenUsage> summarizeTokensPerDay(ProjectId projectId,
                                                Instant from,
                                                Instant to,
                                                ZoneOffset offset);

    default List<DailyTokenUsage> summarizeTokensPerDay(ProjectId projectId,
                                                        UserId ownerId,
                                                        Instant from,
                                                        Instant to,
                                                        ZoneOffset offset) {
        return summarizeTokensPerDay(projectId, from, to, offset);
    }

    /** 按用户时区的整点小时聚合 Token；用于当天和滚动 24 小时趋势。 */
    List<HourlyTokenUsage> summarizeTokensPerHour(ProjectId projectId,
                                                  Instant from,
                                                  Instant to,
                                                  ZoneOffset offset);

    default List<HourlyTokenUsage> summarizeTokensPerHour(ProjectId projectId,
                                                          UserId ownerId,
                                                          Instant from,
                                                          Instant to,
                                                          ZoneOffset offset) {
        return summarizeTokensPerHour(projectId, from, to, offset);
    }

    /**
     * 按自然日统计收场任务（DONE / FAILED），返回的 {@code day} 已在 {@code offset} 时区里。
     *
     * <p>切天必须由 SQL 完成：时间列按约定存 UTC 字面量，拉回内存再分组等于把整张任务表
     * 拖进 JVM。没有数据的日子不返回，补零是应用层的事。
     */
    List<DailyCompletionCount> countCompletionPerDay(ProjectId projectId,
                                                     LocalDate from,
                                                     LocalDate to,
                                                     ZoneOffset offset);

    default List<DailyCompletionCount> countCompletionPerDay(ProjectId projectId,
                                                             UserId ownerId,
                                                             LocalDate from,
                                                             LocalDate to,
                                                             ZoneOffset offset) {
        return countCompletionPerDay(projectId, from, to, offset);
    }
}
