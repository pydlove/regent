package com.pydlove.regent.deployment.infrastructure.persistence;

import com.pydlove.regent.deployment.domain.model.DeploymentAuthType;
import com.pydlove.regent.deployment.domain.model.DeploymentServer;
import com.pydlove.regent.deployment.domain.model.DeploymentServerId;
import com.pydlove.regent.project.domain.model.ProjectId;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** 部署服务器领域对象与数据对象转换。 */
public final class DeploymentServerDataMapper {

    private DeploymentServerDataMapper() {
    }

    public static DeploymentServerData toData(DeploymentServer server) {
        DeploymentServerData data = new DeploymentServerData();
        data.setId(server.getId() == null ? null : server.getId().value());
        data.setProjectId(server.getProjectId().value());
        data.setName(server.getName());
        data.setHost(server.getHost());
        data.setPort(server.getPort());
        data.setSshUsername(server.getUsername());
        data.setAuthType(server.getAuthType().name());
        data.setDefaultRemoteDirectory(server.getDefaultRemoteDirectory());
        data.setCreatedAt(toDb(server.getCreatedAt()));
        data.setUpdatedAt(toDb(server.getUpdatedAt()));
        return data;
    }

    public static DeploymentServer toDomain(DeploymentServerRow row) {
        return DeploymentServer.restore(
                DeploymentServerId.of(row.getId()),
                ProjectId.of(row.getProjectId()),
                row.getName(),
                row.getHost(),
                row.getPort(),
                row.getSshUsername(),
                DeploymentAuthType.parse(row.getAuthType()),
                row.getDefaultRemoteDirectory(),
                toInstant(row.getCreatedAt()),
                toInstant(row.getUpdatedAt()));
    }

    private static LocalDateTime toDb(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
