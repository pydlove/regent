package com.pydlove.regent.execution.infrastructure.persistence;

import com.pydlove.regent.execution.domain.repository.ExecutionControlTowerRepository;
import com.pydlove.regent.execution.domain.repository.ExecutionControlTowerRow;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.ProjectId;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** 控制塔只读端口实现。 */
@Repository
public class MyBatisExecutionControlTowerRepository implements ExecutionControlTowerRepository {

    private final ExecutionControlTowerMapper mapper;

    public MyBatisExecutionControlTowerRepository(ExecutionControlTowerMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<ExecutionControlTowerRow> findEntries(UserId ownerId,
                                                      ProjectId projectId,
                                                      int limit) {
        return mapper.selectEntries(
                        ownerId.value(),
                        projectId == null ? null : projectId.value(),
                        limit)
                .stream()
                .map(MyBatisExecutionControlTowerRepository::toDomain)
                .toList();
    }

    private static ExecutionControlTowerRow toDomain(ExecutionControlTowerRowData data) {
        return new ExecutionControlTowerRow(
                data.getExecutionType(),
                data.getBusinessId(),
                data.getRunId(),
                data.getAttemptNo(),
                data.getProjectId(),
                data.getProjectName(),
                data.getName(),
                data.getBusinessStatus(),
                data.getRunStatus(),
                data.getExecutor(),
                data.getAgentName(),
                data.getExitCode(),
                data.getSummary(),
                instant(data.getStartedAt()),
                instant(data.getFinishedAt()),
                instant(data.getUpdatedAt()),
                instant(data.getLeaseExpiresAt()),
                instant(data.getLastEventAt()));
    }

    private static Instant instant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
