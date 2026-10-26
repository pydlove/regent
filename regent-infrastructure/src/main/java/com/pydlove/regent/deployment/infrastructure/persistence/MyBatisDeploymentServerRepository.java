package com.pydlove.regent.deployment.infrastructure.persistence;

import com.pydlove.regent.deployment.domain.model.DeploymentServer;
import com.pydlove.regent.deployment.domain.model.DeploymentServerId;
import com.pydlove.regent.deployment.domain.repository.DeploymentServerRepository;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.domain.DomainException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 可复用部署服务器仓储实现。 */
@Repository
public class MyBatisDeploymentServerRepository implements DeploymentServerRepository {

    private final DeploymentServerMapper mapper;

    public MyBatisDeploymentServerRepository(DeploymentServerMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<DeploymentServer> findById(DeploymentServerId id) {
        return Optional.ofNullable(mapper.selectById(id.value()))
                .map(this::toDomain);
    }

    @Override
    public Optional<DeploymentServer> findByIdOwned(DeploymentServerId id, UserId ownerId) {
        return Optional.ofNullable(mapper.selectByIdOwned(id.value(), ownerId.value()))
                .map(this::toDomain);
    }

    @Override
    public Optional<DeploymentServer> findByIdForUpdate(DeploymentServerId id) {
        return Optional.ofNullable(mapper.selectByIdForUpdate(id.value()))
                .map(this::toDomain);
    }

    @Override
    public List<DeploymentServer> findAll(ProjectId projectId) {
        return findAll(projectId, null);
    }

    @Override
    public List<DeploymentServer> findAll(ProjectId projectId, UserId ownerId) {
        return toDomains(mapper.selectAll(projectId == null ? null : projectId.value(),
                ownerId == null ? null : ownerId.value()));
    }

    @Override
    public boolean existsByName(Collection<ProjectId> projectIds,
                                String name,
                                DeploymentServerId excludingId) {
        return mapper.countByNameInProjects(projectIds.stream().map(ProjectId::value).toList(), name,
                excludingId == null ? null : excludingId.value()) > 0;
    }

    @Override
    public void save(DeploymentServer server) {
        DeploymentServerData data = DeploymentServerDataMapper.toData(server);
        try {
            if (server.getId() == null) {
                if (mapper.insert(data) != 1) {
                    throw new DomainException("部署服务器新增失败：" + server.getName());
                }
                server.assignIdentity(DeploymentServerId.of(data.getId()));
            } else if (mapper.update(data) != 1) {
                throw new DomainException("部署服务器更新失败或已被删除：" + server.getId());
            }
            replaceProjectLinks(server);
        } catch (DuplicateKeyException e) {
            throw new DomainException("部署服务器保存失败，项目关联可能冲突：" + server.getName(), e);
        }
    }

    @Override
    public void delete(DeploymentServerId id) {
        LocalDateTime now = LocalDateTime.ofInstant(
                Instant.now().truncatedTo(ChronoUnit.MILLIS), ZoneOffset.UTC);
        if (mapper.softDelete(id.value(), now) != 1) {
            throw new DomainException("部署服务器删除失败或已被删除：" + id);
        }
    }

    private DeploymentServer toDomain(DeploymentServerRow row) {
        return toDomains(List.of(row)).get(0);
    }

    private List<DeploymentServer> toDomains(List<DeploymentServerRow> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> serverIds = rows.stream().map(DeploymentServerRow::getId).toList();
        Map<Long, List<Long>> projectIdsByServer = new LinkedHashMap<>();
        for (DeploymentServerProjectRow link : mapper.selectProjectLinks(serverIds)) {
            projectIdsByServer
                    .computeIfAbsent(link.getServerId(), ignored -> new ArrayList<>())
                    .add(link.getProjectId());
        }
        return rows.stream()
                .map(row -> DeploymentServerDataMapper.toDomain(
                        row,
                        projectIdsByServer.getOrDefault(row.getId(), List.of())))
                .toList();
    }

    private void replaceProjectLinks(DeploymentServer server) {
        long serverId = server.getId().value();
        mapper.deleteProjectLinks(serverId);
        mapper.insertProjectLinks(serverId,
                server.getProjectIds().stream().map(ProjectId::value).toList());
    }
}
