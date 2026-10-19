package com.pydlove.regent.execution.infrastructure.persistence;

import org.apache.ibatis.annotations.Param;

/** 任务草稿的 MyBatis 映射接口。 */
public interface TaskDraftMapper {

    TaskDraftData selectByUserId(@Param("userId") long userId);

    int upsert(TaskDraftData data);

    int deleteByUserId(@Param("userId") long userId);
}
