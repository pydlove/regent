package com.pydlove.regent.execution.infrastructure.persistence;

import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 任务文档 MyBatis 映射。 */
public interface TaskDocumentMapper {
    int insert(TaskDocumentData data);
    TaskDocumentData selectById(@Param("id") long id);
    TaskDocumentData selectByIdOwned(@Param("id") long id, @Param("ownerId") long ownerId);
    List<TaskDocumentData> selectByTask(@Param("taskId") long taskId);
}
