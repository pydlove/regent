package com.pydlove.regent.gitcommit.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pydlove.regent.gitcommit.application.query.GitCommitDetailView;
import com.pydlove.regent.gitcommit.application.query.GitCommitFilter;
import com.pydlove.regent.gitcommit.application.query.GitCommitPageView;
import com.pydlove.regent.gitcommit.application.query.GitCommitQueryRepository;
import com.pydlove.regent.gitcommit.application.query.GitCommitSummaryView;
import com.pydlove.regent.gitcommit.domain.model.GitCommitStatus;
import com.pydlove.regent.shared.domain.DomainException;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/** Git 提交只读查询的 MyBatis 实现。 */
@Repository
public class MyBatisGitCommitQueryRepository implements GitCommitQueryRepository {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {
    };

    private final GitCommitQueryMapper mapper;

    public MyBatisGitCommitQueryRepository(GitCommitQueryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<GitCommitSummaryView> findByTask(long taskId, long ownerId) {
        return mapper.selectByTask(taskId, ownerId).stream().map(this::summary).toList();
    }

    @Override
    public GitCommitPageView findPage(long ownerId, GitCommitFilter filter) {
        int offset = (filter.page() - 1) * filter.size();
        List<GitCommitSummaryView> items = mapper.selectPage(ownerId, filter, offset)
                .stream().map(this::summary).toList();
        return new GitCommitPageView(
                items,
                mapper.countPage(ownerId, filter),
                filter.page(),
                filter.size());
    }

    @Override
    public Optional<GitCommitDetailView> findDetail(long id, long ownerId) {
        GitCommitQueryRow row = mapper.selectDetail(id, ownerId);
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(new GitCommitDetailView(
                summary(row),
                row.getReviewDecisionId(),
                row.getRepositoryPath(),
                row.getBaseRef(),
                files(row.getFilePathsJson()),
                row.getFailureReason(),
                row.getOutputSummary()));
    }

    private GitCommitSummaryView summary(GitCommitQueryRow row) {
        return new GitCommitSummaryView(
                row.getId(),
                row.getProjectId(),
                row.getProjectName(),
                row.getTaskId(),
                row.getTaskTitle(),
                row.getTicket(),
                row.getCommitMessage(),
                row.getBranchName(),
                row.getCommitSha(),
                GitCommitStatus.valueOf(row.getStatus()),
                row.getFailureReason(),
                row.getOperatorName(),
                instant(row.getStartedAt()),
                instant(row.getFinishedAt()),
                row.getReviewSnapshotId());
    }

    private static List<String> files(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return JSON.readValue(json, STRINGS);
        } catch (JsonProcessingException e) {
            throw new DomainException("Git 提交文件清单不是合法 JSON", e);
        }
    }

    private static java.time.Instant instant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
