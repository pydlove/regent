package com.pydlove.regent.review.domain.model;

import java.time.Instant;
import java.util.List;

/** 持久化重建快照所需的完整事实。 */
public record ReviewSnapshotState(
        ReviewSnapshotId id,
        ReviewTaskId taskId,
        ReviewTaskRunId taskRunId,
        ReviewProjectId projectId,
        String baseRef,
        String headRef,
        boolean gitRepository,
        String diffHash,
        int fileCount,
        int additions,
        int deletions,
        List<ReviewDiffFile> files,
        String diffContent,
        boolean diffTruncated,
        ReviewSnapshotStatus status,
        ReviewAnalysisStatus analysisStatus,
        String analysisError,
        List<ReviewChangeUnit> changeUnits,
        List<ReviewFinding> findings,
        List<ReviewEvidence> evidence,
        String commitSha,
        String commitMessage,
        ReviewOperatorId committedBy,
        Instant committedAt,
        Instant reviewedAt,
        Instant createdAt,
        Instant updatedAt) {
}
