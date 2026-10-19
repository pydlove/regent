package com.pydlove.regent.execution.infrastructure.persistence;

import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 附件映射接口。SQL 在 {@code mapper/TaskAttachmentMapper.xml}。 */
public interface TaskAttachmentMapper {

    /** 新增。主键由数据库生成，{@code useGeneratedKeys} 回填到 {@link TaskAttachmentData#getId()}。 */
    int insert(TaskAttachmentData data);

    TaskAttachmentData selectById(@Param("id") long id);

    TaskAttachmentData selectByIdOwned(@Param("id") long id, @Param("ownerId") long ownerId);

    List<TaskAttachmentData> selectByTask(@Param("taskId") long taskId);
}
