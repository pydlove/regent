package com.pydlove.regent.execution.infrastructure.persistence;

import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 执行记录写侧的 MyBatis 映射接口。SQL 全部在 {@code mapper/TaskRunMapper.xml}。
 *
 * <p>列表与详情都用同一套列：一个任务最多几十次尝试，没有必要为「列表少两列」再开一套读模型。
 */
public interface TaskRunMapper {

    TaskRunData selectById(@Param("id") long id);

    TaskRunData selectLatestByTask(@Param("taskId") long taskId);

    List<TaskRunData> selectByTask(@Param("taskId") long taskId);

    int insert(TaskRunData data);

    int update(TaskRunData data);
}
