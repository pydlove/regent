package com.pydlove.regent.review.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pydlove.regent.review.domain.model.ReviewAnalysisStatus;
import com.pydlove.regent.review.domain.model.ReviewChangeUnit;
import com.pydlove.regent.review.domain.model.ReviewChangeUnitId;
import com.pydlove.regent.review.domain.model.ReviewChangeUnitKind;
import com.pydlove.regent.review.domain.model.ReviewDiffFile;
import com.pydlove.regent.review.domain.model.ReviewEvidence;
import com.pydlove.regent.review.domain.model.ReviewEvidenceId;
import com.pydlove.regent.review.domain.model.ReviewEvidenceStatus;
import com.pydlove.regent.review.domain.model.ReviewEvidenceType;
import com.pydlove.regent.review.domain.model.ReviewFinding;
import com.pydlove.regent.review.domain.model.ReviewFindingId;
import com.pydlove.regent.review.domain.model.ReviewFindingSeverity;
import com.pydlove.regent.review.domain.model.ReviewFindingStatus;
import com.pydlove.regent.review.domain.model.ReviewHunk;
import com.pydlove.regent.review.domain.model.ReviewProjectId;
import com.pydlove.regent.review.domain.model.ReviewOperatorId;
import com.pydlove.regent.review.domain.model.ReviewRiskLevel;
import com.pydlove.regent.review.domain.model.ReviewSnapshot;
import com.pydlove.regent.review.domain.model.ReviewSnapshotId;
import com.pydlove.regent.review.domain.model.ReviewSnapshotState;
import com.pydlove.regent.review.domain.model.ReviewSnapshotStatus;
import com.pydlove.regent.review.domain.model.ReviewTaskId;
import com.pydlove.regent.review.domain.model.ReviewTaskRunId;
import com.pydlove.regent.review.domain.model.ReviewUnitDecisionStatus;
import com.pydlove.regent.shared.domain.DomainException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** 审阅快照聚合与数据库行之间的唯一转换点。 */
public final class ReviewSnapshotDataMapper {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final TypeReference<List<ReviewHunk>> HUNKS = new TypeReference<>() {
    };
    private static final TypeReference<List<ReviewDiffFile>> FILES = new TypeReference<>() {
    };

    private ReviewSnapshotDataMapper() {
    }

    public static ReviewSnapshotData toData(ReviewSnapshot snapshot) {
        ReviewSnapshotData data = new ReviewSnapshotData();
        data.setId(snapshot.getId() == null ? null : snapshot.getId().value());
        data.setTaskId(snapshot.getTaskId().value());
        data.setTaskRunId(snapshot.getTaskRunId().value());
        data.setProjectId(snapshot.getProjectId().value());
        data.setBaseRef(snapshot.getBaseRef());
        data.setHeadRef(snapshot.getHeadRef());
        data.setGitRepository(snapshot.isGitRepository());
        data.setDiffHash(snapshot.getDiffHash());
        data.setFileCount(snapshot.getFileCount());
        data.setAdditionCount(snapshot.getAdditions());
        data.setDeletionCount(snapshot.getDeletions());
        data.setFilesJson(toJson(snapshot.getFiles()));
        data.setStatus(snapshot.getStatus().name());
        data.setDiffContent(snapshot.getDiffContent());
        data.setDiffTruncated(snapshot.isDiffTruncated());
        data.setAnalysisStatus(snapshot.getAnalysisStatus().name());
        data.setAnalysisError(snapshot.getAnalysisError());
        data.setCommitSha(snapshot.getCommitSha());
        data.setCommitMessage(snapshot.getCommitMessage());
        data.setCommittedBy(snapshot.getCommittedBy() == null
                ? null : snapshot.getCommittedBy().value());
        data.setCommittedAt(toDb(snapshot.getCommittedAt()));
        data.setReviewedAt(toDb(snapshot.getReviewedAt()));
        data.setCreatedAt(toDb(snapshot.getCreatedAt()));
        data.setUpdatedAt(toDb(snapshot.getUpdatedAt()));
        return data;
    }

    public static ReviewSnapshot toDomain(ReviewSnapshotData data,
                                          List<ReviewChangeUnitData> units,
                                          List<ReviewFindingData> findings,
                                          List<ReviewEvidenceData> evidence) {
        return ReviewSnapshot.restore(new ReviewSnapshotState(
                ReviewSnapshotId.of(data.getId()),
                ReviewTaskId.of(data.getTaskId()),
                ReviewTaskRunId.of(data.getTaskRunId()),
                ReviewProjectId.of(data.getProjectId()),
                data.getBaseRef(),
                data.getHeadRef(),
                Boolean.TRUE.equals(data.getGitRepository()),
                data.getDiffHash(),
                nullToZero(data.getFileCount()),
                nullToZero(data.getAdditionCount()),
                nullToZero(data.getDeletionCount()),
                fromJson(data.getFilesJson(), FILES),
                data.getDiffContent(),
                Boolean.TRUE.equals(data.getDiffTruncated()),
                ReviewSnapshotStatus.valueOf(data.getStatus()),
                ReviewAnalysisStatus.valueOf(data.getAnalysisStatus()),
                data.getAnalysisError(),
                units.stream().map(ReviewSnapshotDataMapper::toDomain).toList(),
                findings.stream().map(ReviewSnapshotDataMapper::toDomain).toList(),
                evidence.stream().map(ReviewSnapshotDataMapper::toDomain).toList(),
                data.getCommitSha(),
                data.getCommitMessage(),
                data.getCommittedBy() == null ? null : ReviewOperatorId.of(data.getCommittedBy()),
                fromDb(data.getCommittedAt()),
                fromDb(data.getReviewedAt()),
                fromDb(data.getCreatedAt()),
                fromDb(data.getUpdatedAt())));
    }

    public static ReviewChangeUnitData toData(ReviewChangeUnit unit,
                                               long snapshotId,
                                               Instant createdAt) {
        ReviewChangeUnitData data = new ReviewChangeUnitData();
        data.setId(unit.getId() == null ? null : unit.getId().value());
        data.setSnapshotId(snapshotId);
        data.setSequenceNo(unit.getSequenceNo());
        data.setKind(unit.getKind().name());
        data.setRiskLevel(unit.getRiskLevel().name());
        data.setTitle(unit.getTitle());
        data.setReason(unit.getReason());
        data.setDecisionQuestion(unit.getDecisionQuestion());
        data.setModuleName(unit.getModuleName());
        data.setFilePathsJson(toJson(unit.getFilePaths()));
        data.setKeyHunksJson(toJson(unit.getKeyHunks()));
        data.setRequiresView(unit.isRequiresView());
        data.setViewedAt(toDb(unit.getViewedAt()));
        data.setDecisionStatus(unit.getDecisionStatus().name());
        data.setDecisionComment(unit.getDecisionComment());
        data.setDecidedBy(unit.getDecidedBy() == null ? null : unit.getDecidedBy().value());
        data.setDecidedAt(toDb(unit.getDecidedAt()));
        data.setCreatedAt(toDb(createdAt));
        return data;
    }

    public static ReviewFindingData toData(ReviewFinding finding,
                                           long snapshotId,
                                           Instant createdAt) {
        ReviewFindingData data = new ReviewFindingData();
        data.setId(finding.getId() == null ? null : finding.getId().value());
        data.setSnapshotId(snapshotId);
        data.setChangeUnitId(finding.getChangeUnitId() == null
                ? null : finding.getChangeUnitId().value());
        data.setSeverity(finding.getSeverity().name());
        data.setStatus(finding.getStatus().name());
        data.setTitle(finding.getTitle());
        data.setDescription(finding.getDescription());
        data.setImpact(finding.getImpact());
        data.setFilePath(finding.getFilePath());
        data.setLineStart(finding.getLineStart());
        data.setLineEnd(finding.getLineEnd());
        data.setSuggestedAction(finding.getSuggestedAction());
        data.setEvidenceId(finding.getEvidenceId() == null ? null : finding.getEvidenceId().value());
        data.setCreatedAt(toDb(finding.getCreatedAt() == null ? createdAt : finding.getCreatedAt()));
        data.setUpdatedAt(toDb(finding.getUpdatedAt()));
        return data;
    }

    public static ReviewEvidenceData toData(ReviewEvidence evidence,
                                             long snapshotId,
                                             Instant createdAt) {
        ReviewEvidenceData data = new ReviewEvidenceData();
        data.setId(evidence.getId() == null ? null : evidence.getId().value());
        data.setSnapshotId(snapshotId);
        data.setChangeUnitId(evidence.getChangeUnitId() == null
                ? null : evidence.getChangeUnitId().value());
        data.setEvidenceType(evidence.getType().name());
        data.setStatus(evidence.getStatus().name());
        data.setTitle(evidence.getTitle());
        data.setCommandText(evidence.getCommand());
        data.setSummary(evidence.getSummary());
        data.setSourceType(evidence.getSourceType());
        data.setSourceId(evidence.getSourceId());
        data.setOccurredAt(toDb(evidence.getOccurredAt()));
        data.setCreatedAt(toDb(createdAt));
        return data;
    }

    private static ReviewChangeUnit toDomain(ReviewChangeUnitData data) {
        return ReviewChangeUnit.restore(
                ReviewChangeUnitId.of(data.getId()),
                data.getSequenceNo(),
                ReviewChangeUnitKind.valueOf(data.getKind()),
                ReviewRiskLevel.valueOf(data.getRiskLevel()),
                data.getTitle(),
                data.getReason(),
                data.getDecisionQuestion(),
                data.getModuleName(),
                fromJson(data.getFilePathsJson(), STRING_LIST),
                fromJson(data.getKeyHunksJson(), HUNKS),
                Boolean.TRUE.equals(data.getRequiresView()),
                fromDb(data.getViewedAt()),
                data.getDecisionStatus() == null
                        ? ReviewUnitDecisionStatus.PENDING
                        : ReviewUnitDecisionStatus.valueOf(data.getDecisionStatus()),
                data.getDecisionComment(),
                data.getDecidedBy() == null ? null : ReviewOperatorId.of(data.getDecidedBy()),
                fromDb(data.getDecidedAt()));
    }

    private static ReviewFinding toDomain(ReviewFindingData data) {
        return ReviewFinding.restore(
                ReviewFindingId.of(data.getId()),
                data.getChangeUnitId() == null ? null : ReviewChangeUnitId.of(data.getChangeUnitId()),
                ReviewFindingSeverity.valueOf(data.getSeverity()),
                ReviewFindingStatus.valueOf(data.getStatus()),
                data.getTitle(),
                data.getDescription(),
                data.getImpact(),
                data.getFilePath(),
                data.getLineStart(),
                data.getLineEnd(),
                data.getSuggestedAction(),
                data.getEvidenceId() == null ? null : ReviewEvidenceId.of(data.getEvidenceId()),
                fromDb(data.getCreatedAt()),
                fromDb(data.getUpdatedAt()));
    }

    private static ReviewEvidence toDomain(ReviewEvidenceData data) {
        return ReviewEvidence.restore(
                ReviewEvidenceId.of(data.getId()),
                data.getChangeUnitId() == null ? null : ReviewChangeUnitId.of(data.getChangeUnitId()),
                ReviewEvidenceType.valueOf(data.getEvidenceType()),
                ReviewEvidenceStatus.valueOf(data.getStatus()),
                data.getTitle(),
                data.getCommandText(),
                data.getSummary(),
                data.getSourceType(),
                data.getSourceId(),
                fromDb(data.getOccurredAt()));
    }

    private static <T> String toJson(T value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new DomainException("审阅结构化字段无法序列化", e);
        }
    }

    private static <T> List<T> fromJson(String value, TypeReference<List<T>> type) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return JSON.readValue(value, type);
        } catch (JsonProcessingException e) {
            throw new DomainException("审阅结构化字段不是合法 JSON：" + value, e);
        }
    }

    private static int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }

    static LocalDateTime toDb(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    static Instant fromDb(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
