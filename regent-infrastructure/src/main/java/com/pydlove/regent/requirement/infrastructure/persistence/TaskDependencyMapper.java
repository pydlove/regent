package com.pydlove.regent.requirement.infrastructure.persistence;

import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 任务依赖关系映射。 */
public interface TaskDependencyMapper {

    List<Long> selectDependencies(@Param("taskId") long taskId);

    int deleteByTask(@Param("taskId") long taskId);

    int insertDependencies(@Param("taskId") long taskId,
                           @Param("dependsOnTaskIds") List<Long> dependsOnTaskIds);
}
