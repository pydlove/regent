package com.pydlove.regent.gitcommit.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pydlove.regent.gitcommit.domain.model.GitCommitDecisionId;
import com.pydlove.regent.gitcommit.domain.model.GitCommitOperatorId;
import com.pydlove.regent.gitcommit.domain.model.GitCommitProjectId;
import com.pydlove.regent.gitcommit.domain.model.GitCommitRecord;
import com.pydlove.regent.gitcommit.domain.model.GitCommitRecordId;
import com.pydlove.regent.gitcommit.domain.model.GitCommitSnapshotId;
import com.pydlove.regent.gitcommit.domain.model.GitCommitStatus;
import com.pydlove.regent.gitcommit.domain.model.GitCommitTaskId;
import com.pydlove.regent.gitcommit.domain.model.GitCommitTaskRunId;
import com.pydlove.regent.shared.domain.DomainException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** Git 提交聚合与数据库行之间的唯一转换点。 */
public final class GitCommitRecordDataMapper {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {
    };

    private GitCommitRecordDataMapper() {
    }

    public static GitCommitRecordData toData(GitCommitRecord record) {
        GitCommitRecordData data = new GitCommitRecordData();
        data.setId(record.getId() == null ? null : record.getId().value());
        data.setProjectId(record.getProjectId().value());
        data.setTaskId(record.getTaskId().value());
        data.setTaskRunId(record.getTaskRunId().value());
        data.setReviewSnapshotId(record.getSnapshotId().value());
        data.setReviewDecisionId(record.getDecisionId().value());
        data.setOperatorId(record.getOperatorId().value());
        data.setProjectName(record.getProjectName());
        data.setTaskTitle(record.getTaskTitle());
        data.setRepositoryPath(record.getRepositoryPath());
        data.setTicket(record.getTicket());
        data.setCommitMessage(record.getMessage());
        data.setBranchName(record.getBranchName());
        data.setBaseRef(record.getBaseRef());
        data.setCommitSha(record.getCommitSha());
        data.setFilePathsJson(toJson(record.getFilePaths()));
        data.setStatus(record.getStatus().name());
        data.setFailureReason(record.getFailureReason());
        data.setOutputSummary(record.getOutputSummary());
        data.setStartedAt(toDb(record.getStartedAt()));
        data.setFinishedAt(toDb(record.getFinishedAt()));
        data.setCreatedAt(toDb(record.getCreatedAt()));
        data.setUpdatedAt(toDb(record.getUpdatedAt()));
        return data;
    }

    public static GitCommitRecord toDomain(GitCommitRecordData data) {
        return GitCommitRecord.restore(
                GitCommitRecordId.of(data.getId()),
                GitCommitProjectId.of(data.getProjectId()),
                GitCommitTaskId.of(data.getTaskId()),
                GitCommitTaskRunId.of(data.getTaskRunId()),
                GitCommitSnapshotId.of(data.getReviewSnapshotId()),
                GitCommitDecisionId.of(data.getReviewDecisionId()),
                GitCommitOperatorId.of(data.getOperatorId()),
                data.getProjectName(),
                data.getTaskTitle(),
                data.getRepositoryPath(),
                data.getTicket(),
                data.getCommitMessage(),
                data.getBranchName(),
                data.getBaseRef(),
                fromJson(data.getFilePathsJson()),
                GitCommitStatus.valueOf(data.getStatus()),
                data.getCommitSha(),
                data.getFailureReason(),
                data.getOutputSummary(),
                fromDb(data.getStartedAt()),
                fromDb(data.getFinishedAt()),
                fromDb(data.getCreatedAt()),
                fromDb(data.getUpdatedAt()));
    }

    private static String toJson(List<String> values) {
        try {
            return JSON.writeValueAsString(values);
        } catch (JsonProcessingException e) {
            throw new DomainException("Git 提交文件清单无法序列化", e);
        }
    }

    private static List<String> fromJson(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return JSON.readValue(value, STRINGS);
        } catch (JsonProcessingException e) {
            throw new DomainException("Git 提交文件清单不是合法 JSON", e);
        }
    }

    static LocalDateTime toDb(Instant value) {
        return value == null ? null : LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    static Instant fromDb(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
