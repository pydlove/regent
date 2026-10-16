package com.pydlove.regent.execution.infrastructure.persistence;

import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/** 执行设置单行表的 MyBatis 映射接口。SQL 在 {@code mapper/ExecutionSettingsMapper.xml}。 */
public interface ExecutionSettingsMapper {

    ExecutionSettingsRow select();

    int upsert(@Param("id") int id,
               @Param("maxConcurrentTasks") int maxConcurrentTasks,
               @Param("updatedAt") LocalDateTime updatedAt);
}
