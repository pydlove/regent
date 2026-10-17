package com.pydlove.regent.execution.infrastructure.persistence;

import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * MyBatis 映射接口，只认 {@link TaskData}。SQL 全部在 {@code mapper/TaskMapper.xml}，
 * 不做注解 SQL —— 列表条件与分页拼起来后，注解里的字符串会难以阅读和调试。
 */
public interface TaskMapper {

    TaskData selectById(@Param("id") long id);

    TaskData selectByIdForUpdate(@Param("id") long id);

    List<TaskListRow> selectListPage(@Param("status") String status,
                                     @Param("level") String level,
                                     @Param("projectId") Long projectId,
                                     @Param("keyword") String keyword,
                                     @Param("size") int size,
                                     @Param("offset") int offset);

    long countListBy(@Param("status") String status,
                     @Param("level") String level,
                     @Param("projectId") Long projectId,
                     @Param("keyword") String keyword);

    /** 返回 {@code status -> count}，缺失的状态不会出现在结果里。 */
    List<Map<String, Object>> countGroupByStatus();

    /** 汇总全部有效任务已结算的 token。 */
    TokenUsageRow summarizeTokens();

    /**
     * 按天统计收场任务。{@code from} / {@code to} 是 UTC 字面量边界（左闭右开），
     * {@code offsetMinutes} 只用于把 UTC 字面量平移回调用方时区再取日期。
     */
    List<DailyCompletionRow> countCompletionPerDay(@Param("from") LocalDateTime from,
                                                   @Param("to") LocalDateTime to,
                                                   @Param("offsetMinutes") int offsetMinutes);

    /** 可派发的候选任务：待执行/排队中的，以及决策已批准、等待断点续跑的。 */
    List<TaskData> selectDispatchable(@Param("limit") int limit);

    List<TaskData> selectDispatchableInProject(@Param("projectId") long projectId, @Param("limit") int limit);

    /** 用户明确提交等待执行位的任务。 */
    List<TaskData> selectQueued(@Param("limit") int limit);

    int insert(TaskData data);

    int update(TaskData data);

    int updateEditable(TaskData data);

    int softDelete(@Param("id") long id, @Param("updatedAt") LocalDateTime updatedAt);
}
