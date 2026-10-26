package com.pydlove.regent.review.infrastructure.persistence;

import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 审阅结论只追加。 */
public interface ReviewDecisionMapper {

    List<ReviewDecisionData> selectBySnapshot(@Param("snapshotId") long snapshotId);

    ReviewDecisionData selectLatestBySnapshot(@Param("snapshotId") long snapshotId);

    int insert(ReviewDecisionData data);
}
