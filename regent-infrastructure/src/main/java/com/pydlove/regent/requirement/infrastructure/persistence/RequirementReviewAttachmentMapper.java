package com.pydlove.regent.requirement.infrastructure.persistence;

import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 需求评审会议纪要附件 SQL 映射。 */
public interface RequirementReviewAttachmentMapper {

    RequirementReviewAttachmentData selectById(@Param("id") long id);

    List<RequirementReviewAttachmentData> selectByRecord(@Param("recordId") long recordId);

    int insert(RequirementReviewAttachmentData data);
}
