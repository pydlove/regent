package com.pydlove.regent.execution.infrastructure.persistence;

import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * MyBatis 映射接口，只认 {@link TaskData}。SQL 全部在 {@code mapper/TaskMapper.xml}，
 * 不做注解 SQL —— 列表条件与分页拼起来后，注解里的字符串会难以阅读和调试。
 */
public interface TaskMapper {

    TaskData selectById(@Param("id") long id);

    List<TaskListRow> selectListPage(@Param("status") String status,
                                     @Param("projectId") Long projectId,
                                     @Param("keyword") String keyword,
                                     @Param("size") int size,
                                     @Param("offset") int offset);

    long countListBy(@Param("status") String status,
                     @Param("projectId") Long projectId,
                     @Param("keyword") String keyword);

    /** 返回 {@code status -> count}，缺失的状态不会出现在结果里。 */
    List<Map<String, Object>> countGroupByStatus();

    /** 可派发的候选任务：待执行的，以及决策已批准、等待断点续跑的。 */
    List<TaskData> selectDispatchable(@Param("limit") int limit);

    int insert(TaskData data);

    int update(TaskData data);
}
