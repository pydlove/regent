package com.pydlove.regent.deployment.infrastructure.persistence;

import com.pydlove.regent.deployment.domain.model.DeploymentId;
import com.pydlove.regent.deployment.domain.model.DeploymentRun;
import com.pydlove.regent.deployment.domain.model.DeploymentRunId;
import com.pydlove.regent.deployment.domain.repository.DeploymentRunRepository;
import com.pydlove.regent.shared.domain.DomainException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** {@link DeploymentRunRepository} 的 MyBatis 实现。 */
@Repository
public class MyBatisDeploymentRunRepository implements DeploymentRunRepository {

    private final DeploymentRunMapper mapper;

    public MyBatisDeploymentRunRepository(DeploymentRunMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<DeploymentRun> findById(DeploymentRunId id) {
        return Optional.ofNullable(mapper.selectById(id.value()))
                .map(DeploymentDataMapper::toDomain);
    }

    @Override
    public List<DeploymentRun> findByDeployment(DeploymentId deploymentId, int limit) {
        return mapper.selectByDeployment(deploymentId.value(), limit).stream()
                .map(DeploymentDataMapper::toDomain)
                .toList();
    }

    @Override
    public List<DeploymentRun> findLatestByDeploymentIds(List<DeploymentId> deploymentIds) {
        if (deploymentIds == null || deploymentIds.isEmpty()) {
            return List.of();
        }
        return mapper.selectLatestByDeploymentIds(
                        deploymentIds.stream().map(DeploymentId::value).toList()).stream()
                .map(DeploymentDataMapper::toDomain)
                .toList();
    }

    @Override
    public List<DeploymentRun> findActive() {
        return mapper.selectActive().stream().map(DeploymentDataMapper::toDomain).toList();
    }

    @Override
    public boolean existsActiveByDeployment(DeploymentId deploymentId) {
        return mapper.countActiveByDeployment(deploymentId.value()) > 0;
    }

    @Override
    public void save(DeploymentRun run) {
        DeploymentRunData data = DeploymentDataMapper.toData(run);
        if (run.getId() == null) {
            if (mapper.insert(data) != 1) {
                throw new DomainException("部署执行记录新增失败");
            }
            run.assignIdentity(DeploymentRunId.of(data.getId()));
            return;
        }
        if (mapper.update(data) != 1) {
            throw new DomainException("部署执行记录更新失败：" + run.getId());
        }
    }
}
