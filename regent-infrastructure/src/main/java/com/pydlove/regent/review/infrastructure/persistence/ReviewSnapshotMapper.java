package com.pydlove.regent.review.infrastructure.persistence;

import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 审阅快照及子实体的 MyBatis 映射。 */
public interface ReviewSnapshotMapper {

    ReviewSnapshotData selectById(@Param("id") long id);

    ReviewSnapshotData selectLatestByTask(@Param("taskId") long taskId);

    ReviewSnapshotData selectByTaskRun(@Param("taskRunId") long taskRunId);

    List<ReviewSnapshotData> selectUnexpiredByTask(@Param("taskId") long taskId);

    List<ReviewChangeUnitData> selectUnits(@Param("snapshotId") long snapshotId);

    List<ReviewFindingData> selectFindings(@Param("snapshotId") long snapshotId);

    List<ReviewEvidenceData> selectEvidence(@Param("snapshotId") long snapshotId);

    int insert(ReviewSnapshotData data);

    int update(ReviewSnapshotData data);

    int insertUnit(ReviewChangeUnitData data);

    int updateUnit(ReviewChangeUnitData data);

    int insertFinding(ReviewFindingData data);

    int updateFinding(ReviewFindingData data);

    int insertEvidence(ReviewEvidenceData data);

    int updateEvidence(ReviewEvidenceData data);
}
