package com.pydlove.regent.execution.infrastructure.persistence;

import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 任务开发规范快照 MyBatis 映射。 */
public interface TaskCodeStandardMapper {

    int insert(TaskCodeStandardData data);

    List<TaskCodeStandardData> selectByTask(@Param("taskId") long taskId);

    TaskCodeStandardData selectByIdOwned(@Param("id") long id, @Param("ownerId") long ownerId);
}
