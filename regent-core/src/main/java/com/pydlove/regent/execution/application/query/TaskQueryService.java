package com.pydlove.regent.execution.application.query;

import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.model.TaskStatus;
import com.pydlove.regent.execution.domain.repository.DailyCompletionCount;
import com.pydlove.regent.execution.domain.repository.TaskListItemPage;
import com.pydlove.regent.execution.domain.repository.TaskQuery;
import com.pydlove.regent.execution.domain.repository.TaskQueryRepository;
import com.pydlove.regent.execution.domain.repository.TaskRepository;
import com.pydlove.regent.shared.domain.EntityNotFoundException;
import com.pydlove.regent.shared.kernel.TokenUsage;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务读侧用例。刻意只做编排、不做判断：筛选与分页下推给仓储的 SQL，
 * 聚合内部的状态判断留在领域方法里。
 */
public class TaskQueryService {

    private final TaskRepository taskRepository;
    private final TaskQueryRepository taskQueryRepository;
    private final ZoneId zone;

    /**
     * {@code zone} 决定「一天」从几点切开。工作台上的柱状图与列表里的「几分钟前」必须是同一套
     * 时间口径，所以时区由装配方显式给进来，不在服务里读系统默认值。
     */
    public TaskQueryService(TaskRepository taskRepository,
                            TaskQueryRepository taskQueryRepository,
                            ZoneId zone) {
        this.taskRepository = taskRepository;
        this.taskQueryRepository = taskQueryRepository;
        this.zone = zone;
    }

    public TaskListItemPage list(TaskQuery query) {
        return taskQueryRepository.findPage(query);
    }

    /** 详情取聚合：状态守卫、可执行动作都以聚合为准，不另开一份读模型。 */
    public Task get(TaskId id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("任务不存在：" + id));
    }

    /** 状态分布。缺失的状态补 0，让调用方拿到的键集合始终覆盖全部状态。 */
    public Map<TaskStatus, Long> statusCounts() {
        Map<TaskStatus, Long> counts = new EnumMap<>(TaskStatus.class);
        for (TaskStatus status : TaskStatus.values()) {
            counts.put(status, 0L);
        }
        counts.putAll(taskQueryRepository.countGroupByStatus());
        return counts;
    }

    /** 工作台用的全平台 token 汇总，只统计未逻辑删除任务下的执行记录。 */
    public TokenUsage tokenUsage() {
        return taskQueryRepository.summarizeTokens();
    }

    /**
     * 近 {@code days} 天的收场趋势，含今天，逐日补齐。
     *
     * <p>补零放在应用层：调用方拿到的长度恒等于天数、日期连续，画图不必再处理空档。
     * 切天偏移取「此刻」的偏移量；本项目按中国时区使用（无夏令时），不做跨时区切换的补偿。
     */
    public List<DailyCompletionCount> completionTrend(int days) {
        if (days < 1 || days > 30) {
            throw new IllegalArgumentException("趋势天数必须在 1 到 30 之间");
        }
        LocalDate today = LocalDate.now(zone);
        LocalDate from = today.minusDays(days - 1L);
        Map<LocalDate, DailyCompletionCount> counted = new HashMap<>();
        for (DailyCompletionCount count : taskQueryRepository
                .countCompletionPerDay(from, today, zone.getRules().getOffset(Instant.now()))) {
            counted.put(count.day(), count);
        }
        List<DailyCompletionCount> series = new ArrayList<>(days);
        for (LocalDate day = from; !day.isAfter(today); day = day.plusDays(1)) {
            series.add(counted.getOrDefault(day, new DailyCompletionCount(day, 0L, 0L)));
        }
        return series;
    }
}
