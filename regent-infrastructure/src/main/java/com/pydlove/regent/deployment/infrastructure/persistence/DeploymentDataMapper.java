package com.pydlove.regent.deployment.infrastructure.persistence;

import com.pydlove.regent.deployment.domain.model.Deployment;
import com.pydlove.regent.deployment.domain.model.DeploymentAuthType;
import com.pydlove.regent.deployment.domain.model.DeploymentId;
import com.pydlove.regent.deployment.domain.model.DeploymentRun;
import com.pydlove.regent.deployment.domain.model.DeploymentRunId;
import com.pydlove.regent.deployment.domain.model.DeploymentRunStatus;
import com.pydlove.regent.project.domain.model.ProjectId;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** 部署领域对象与 MyBatis 数据对象转换。 */
public final class DeploymentDataMapper {

    private DeploymentDataMapper() {
    }

    public static DeploymentData toData(Deployment deployment) {
        DeploymentData data = new DeploymentData();
        data.setId(deployment.getId() == null ? null : deployment.getId().value());
        data.setProjectId(deployment.getProjectId().value());
        data.setName(deployment.getName());
        data.setHost(deployment.getHost());
        data.setPort(deployment.getPort());
        data.setSshUsername(deployment.getUsername());
        data.setAuthType(deployment.getAuthType().name());
        data.setRemoteDirectory(deployment.getRemoteDirectory());
        data.setScriptFilename(deployment.getScriptFilename());
        data.setScriptContent(deployment.getScriptContent());
        data.setScriptHash(deployment.getScriptHash());
        data.setCreatedAt(toDb(deployment.getCreatedAt()));
        data.setUpdatedAt(toDb(deployment.getUpdatedAt()));
        return data;
    }

    public static Deployment toDomain(DeploymentRow row) {
        return Deployment.restore(
                DeploymentId.of(row.getId()),
                ProjectId.of(row.getProjectId()),
                row.getName(),
                row.getHost(),
                row.getPort(),
                row.getSshUsername(),
                DeploymentAuthType.parse(row.getAuthType()),
                row.getRemoteDirectory(),
                row.getScriptFilename(),
                row.getScriptContent(),
                row.getScriptHash(),
                toInstant(row.getCreatedAt()),
                toInstant(row.getUpdatedAt()));
    }

    public static DeploymentRunData toData(DeploymentRun run) {
        DeploymentRunData data = new DeploymentRunData();
        data.setId(run.getId() == null ? null : run.getId().value());
        data.setDeploymentId(run.getDeploymentId().value());
        data.setProjectId(run.getProjectId().value());
        data.setDeploymentName(run.getDeploymentName());
        data.setHost(run.getHost());
        data.setPort(run.getPort());
        data.setSshUsername(run.getUsername());
        data.setAuthType(run.getAuthType().name());
        data.setRemoteDirectory(run.getRemoteDirectory());
        data.setScriptFilename(run.getScriptFilename());
        data.setScriptContent(run.getScriptContent());
        data.setScriptHash(run.getScriptHash());
        data.setStatus(run.getStatus().name());
        data.setExitCode(run.getExitCode());
        data.setSummary(run.getSummary());
        data.setQueuedAt(toDb(run.getQueuedAt()));
        data.setStartedAt(toDb(run.getStartedAt()));
        data.setFinishedAt(toDb(run.getFinishedAt()));
        data.setCreatedAt(toDb(run.getCreatedAt()));
        return data;
    }

    public static DeploymentRun toDomain(DeploymentRunRow row) {
        return DeploymentRun.restore(
                DeploymentRunId.of(row.getId()),
                DeploymentId.of(row.getDeploymentId()),
                ProjectId.of(row.getProjectId()),
                row.getDeploymentName(),
                row.getHost(),
                row.getPort(),
                row.getSshUsername(),
                DeploymentAuthType.parse(row.getAuthType()),
                row.getRemoteDirectory(),
                row.getScriptFilename(),
                row.getScriptContent(),
                row.getScriptHash(),
                DeploymentRunStatus.parse(row.getStatus()),
                row.getExitCode(),
                row.getSummary(),
                toInstant(row.getQueuedAt()),
                toInstant(row.getStartedAt()),
                toInstant(row.getFinishedAt()),
                toInstant(row.getCreatedAt()));
    }

    private static LocalDateTime toDb(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
