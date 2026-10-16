package com.pydlove.regent.bug.infrastructure.persistence;

import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 缺陷单 MyBatis 映射。 */
public interface BugMapper {

    BugData selectDataById(@Param("id") long id);

    BugData selectDataByIdForUpdate(@Param("id") long id);

    BugRow selectRowById(@Param("id") long id);

    List<BugRow> selectPage(@Param("severity") String severity,
                            @Param("scope") String scope,
                            @Param("keyword") String keyword,
                            @Param("size") int size,
                            @Param("offset") int offset);

    long countBy(@Param("severity") String severity,
                 @Param("scope") String scope,
                 @Param("keyword") String keyword);

    int insert(BugData data);

    int update(BugData data);
}
