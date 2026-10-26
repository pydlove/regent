package com.pydlove.regent.execution.infrastructure.persistence;

import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 开发规范条目 MyBatis 映射。 */
public interface CodeStandardMapper {

    int insert(CodeStandardData data);

    int update(CodeStandardData data);

    CodeStandardData selectById(@Param("id") long id);

    CodeStandardData selectByIdOwned(@Param("id") long id, @Param("ownerId") long ownerId);

    List<CodeStandardData> selectByProject(@Param("projectId") long projectId);

    CodeStandardData selectByProjectAndName(@Param("projectId") long projectId,
                                            @Param("name") String name);
}
