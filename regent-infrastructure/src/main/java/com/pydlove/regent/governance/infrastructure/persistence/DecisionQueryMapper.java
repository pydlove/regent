package com.pydlove.regent.governance.infrastructure.persistence;

import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 决策单查询的 MyBatis 映射接口，只读。SQL 见 {@code mapper/DecisionQueryMapper.xml}。 */
public interface DecisionQueryMapper {

    List<DecisionRow> selectPage(@Param("level") String level,
                                 @Param("projectId") Long projectId,
                                 @Param("scope") String scope,
                                 @Param("keyword") String keyword,
                                 @Param("ownerId") Long ownerId,
                                 @Param("size") int size,
                                 @Param("offset") int offset);

    long countBy(@Param("level") String level,
                 @Param("projectId") Long projectId,
                 @Param("scope") String scope,
                 @Param("keyword") String keyword,
                 @Param("ownerId") Long ownerId);

    DecisionRow selectById(@Param("id") long id);

    DecisionRow selectByIdOwned(@Param("id") long id, @Param("ownerId") long ownerId);
}
