package com.pydlove.regent.review.interfaces.dto;

import com.pydlove.regent.review.domain.model.ReviewSnapshot;
import com.pydlove.regent.review.domain.model.ReviewSnapshotStatus;

import java.time.Instant;
import java.util.List;

/** 任务详情内嵌的审阅快照视图。 */
public record ReviewSnapshotResponse(
        long id,
        long taskId,
        long taskRunId,
        long projectId,
        String baseRef,
        String headRef,
        boolean gitRepository,
        String diffHash,
        int fileCount,
        int additions,
        int deletions,
        String status,
        String analysisStatus,
        String analysisError,
        boolean diffTruncated,
        int requiredUnitCount,
        int viewedRequiredUnitCount,
        int approvedUnitCount,
        int changeRequestedUnitCount,
        int rejectedUnitCount,
        long unresolvedBlockerCount,
        boolean canApprove,
        boolean deployable,
        String commitSha,
        String commitMessage,
        long committedBy,
        Instant committedAt,
        Instant reviewedAt,
        Instant createdAt,
        Instant updatedAt,
        List<ReviewChangeUnitResponse> changeUnits,
        List<ReviewFindingResponse> findings,
        List<ReviewEvidenceResponse> evidence) {

    public static ReviewSnapshotResponse from(ReviewSnapshot snapshot) {
        int required = snapshot.requiredUnitCount();
        int viewed = snapshot.viewedRequiredUnitCount();
        long blockers = snapshot.unresolvedBlockerCount();
        return new ReviewSnapshotResponse(
                snapshot.getId().value(),
                snapshot.getTaskId().value(),
                snapshot.getTaskRunId().value(),
                snapshot.getProjectId().value(),
                snapshot.getBaseRef(),
                snapshot.getHeadRef(),
                snapshot.isGitRepository(),
                snapshot.getDiffHash(),
                snapshot.getFileCount(),
                snapshot.getAdditions(),
                snapshot.getDeletions(),
                snapshot.getStatus().name(),
                snapshot.getAnalysisStatus().name(),
                snapshot.getAnalysisError(),
                snapshot.isDiffTruncated(),
                required,
                viewed,
                snapshot.approvedUnitCount(),
                snapshot.changeRequestedUnitCount(),
                snapshot.rejectedUnitCount(),
                blockers,
                snapshot.canApprove(),
                snapshot.isDeployable(),
                snapshot.getCommitSha(),
                snapshot.getCommitMessage(),
                snapshot.getCommittedBy() == null ? 0 : snapshot.getCommittedBy().value(),
                snapshot.getCommittedAt(),
                snapshot.getReviewedAt(),
                snapshot.getCreatedAt(),
                snapshot.getUpdatedAt(),
                snapshot.getChangeUnits().stream().map(ReviewChangeUnitResponse::from).toList(),
                snapshot.getFindings().stream().map(ReviewFindingResponse::from).toList(),
                snapshot.getEvidence().stream().map(ReviewEvidenceResponse::from).toList());
    }
}
