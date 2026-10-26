package com.pydlove.regent.requirement.infrastructure.persistence;

import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 线下需求评审记录 SQL 映射。 */
public interface RequirementReviewRecordMapper {

    RequirementReviewRecordData selectById(@Param("id") long id);

    List<RequirementReviewRecordData> selectByVersion(@Param("versionId") long versionId);

    int selectNextReviewNo(@Param("versionId") long versionId);

    int insert(RequirementReviewRecordData data);

    int update(RequirementReviewRecordData data);
}
