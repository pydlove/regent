package com.pydlove.regent.deployment.infrastructure.persistence;

import com.pydlove.regent.deployment.domain.model.Deployment;
import com.pydlove.regent.deployment.domain.model.DeploymentId;
import com.pydlove.regent.deployment.domain.model.DeploymentServerId;
import com.pydlove.regent.deployment.domain.repository.DeploymentRepository;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.domain.DomainException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/** {@link DeploymentRepository} 的 MyBatis 实现。 */
@Repository
public class MyBatisDeploymentRepository implements DeploymentRepository {

    private final DeploymentMapper mapper;

    public MyBatisDeploymentRepository(DeploymentMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<Deployment> findById(DeploymentId id) {
        return Optional.ofNullable(mapper.selectById(id.value())).map(DeploymentDataMapper::toDomain);
    }

    @Override
    public Optional<Deployment> findByIdOwned(DeploymentId id, UserId ownerId) {
        return Optional.ofNullable(mapper.selectByIdOwned(id.value(), ownerId.value()))
                .map(DeploymentDataMapper::toDomain);
    }

    @Override
    public Optional<Deployment> findByIdForUpdate(DeploymentId id) {
        return Optional.ofNullable(mapper.selectByIdForUpdate(id.value()))
                .map(DeploymentDataMapper::toDomain);
    }

    @Override
    public List<Deployment> findAll(ProjectId projectId) {
        return findAll(projectId, null);
    }

    @Override
    public List<Deployment> findAll(ProjectId projectId, UserId ownerId) {
        return mapper.selectAll(projectId == null ? null : projectId.value(),
                        ownerId == null ? null : ownerId.value()).stream()
                .map(DeploymentDataMapper::toDomain)
                .toList();
    }

    @Override
    public boolean existsByName(ProjectId projectId, String name, DeploymentId excludingId) {
        return mapper.countByName(projectId.value(), name,
                excludingId == null ? null : excludingId.value()) > 0;
    }

    @Override
    public List<DeploymentId> findIdsByServerId(DeploymentServerId serverId) {
        return mapper.selectIdsByServerId(serverId.value()).stream()
                .map(DeploymentId::of)
                .toList();
    }

    @Override
    public int countByServerId(DeploymentServerId serverId) {
        return mapper.countByServerId(serverId.value());
    }

    @Override
    public void save(Deployment deployment) {
        DeploymentData data = DeploymentDataMapper.toData(deployment);
        try {
            if (deployment.getId() == null) {
                if (mapper.insert(data) != 1) {
                    throw new DomainException("部署新增失败：" + deployment.getName());
                }
                deployment.assignIdentity(DeploymentId.of(data.getId()));
                return;
            }
            if (mapper.update(data) != 1) {
                throw new DomainException("部署更新失败或已被删除：" + deployment.getId());
            }
        } catch (DuplicateKeyException e) {
            throw new DomainException("部署保存失败，名称可能重复：" + deployment.getName(), e);
        }
    }

    @Override
    public void delete(DeploymentId id) {
        LocalDateTime now = LocalDateTime.ofInstant(
                Instant.now().truncatedTo(ChronoUnit.MILLIS), ZoneOffset.UTC);
        if (mapper.softDelete(id.value(), now) != 1) {
            throw new DomainException("部署删除失败或已被删除：" + id);
        }
    }
}
