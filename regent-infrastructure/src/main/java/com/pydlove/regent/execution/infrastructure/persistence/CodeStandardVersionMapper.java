package com.pydlove.regent.execution.infrastructure.persistence;

import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 开发规范版本 MyBatis 映射。 */
public interface CodeStandardVersionMapper {

    int insert(CodeStandardVersionData data);

    CodeStandardVersionData selectById(@Param("id") long id);

    CodeStandardVersionData selectByIdOwned(@Param("id") long id, @Param("ownerId") long ownerId);

    CodeStandardVersionData selectCurrent(@Param("standardId") long standardId);

    List<CodeStandardVersionData> selectByStandard(@Param("standardId") long standardId);

    Integer selectMaxVersionNo(@Param("standardId") long standardId);
}
