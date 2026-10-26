package com.pydlove.regent.requirement.infrastructure.persistence;

import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** 需求 SQL 映射。 */
public interface RequirementMapper {

    int insertNumberSequence(RequirementNumberSequenceData data);

    RequirementData selectById(@Param("id") long id);

    RequirementData selectByIdOwned(@Param("id") long id, @Param("ownerId") long ownerId);

    List<RequirementData> selectByProject(@Param("projectId") long projectId);

    List<RequirementData> selectByProjectOwned(@Param("projectId") long projectId,
                                                @Param("ownerId") long ownerId);

    List<RequirementData> selectAllOwned(@Param("ownerId") long ownerId);

    RequirementData selectDeletedByIdOwned(@Param("id") long id,
                                           @Param("ownerId") long ownerId);

    List<RequirementData> selectDeletedByProjectOwned(@Param("projectId") long projectId,
                                                       @Param("ownerId") long ownerId);

    int countByVersion(@Param("versionId") long versionId);

    int insert(RequirementData data);

    int update(RequirementData data);

    int moveToTrash(@Param("id") long id,
                    @Param("deletedBy") long deletedBy,
                    @Param("deletedAt") LocalDateTime deletedAt);

    int restore(@Param("id") long id,
                @Param("updatedAt") LocalDateTime updatedAt);
}
