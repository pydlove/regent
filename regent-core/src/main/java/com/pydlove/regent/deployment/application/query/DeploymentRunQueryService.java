package com.pydlove.regent.deployment.application.query;

import com.pydlove.regent.deployment.domain.model.DeploymentId;
import com.pydlove.regent.deployment.domain.model.DeploymentRun;
import com.pydlove.regent.deployment.domain.model.DeploymentRunId;
import com.pydlove.regent.deployment.domain.port.DeploymentLogStore;
import com.pydlove.regent.deployment.domain.repository.DeploymentRunRepository;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.shared.domain.EntityNotFoundException;

import java.util.List;

/** 部署执行记录与日志读侧。 */
public class DeploymentRunQueryService {

    private final DeploymentRunRepository runRepository;
    private final DeploymentLogStore logStore;

    public DeploymentRunQueryService(DeploymentRunRepository runRepository,
                                     DeploymentLogStore logStore) {
        this.runRepository = runRepository;
        this.logStore = logStore;
    }

    public List<DeploymentRun> listByDeployment(DeploymentId deploymentId, int limit) {
        return runRepository.findByDeployment(deploymentId, Math.max(1, Math.min(limit, 100)));
    }

    public DeploymentRun get(DeploymentRunId id) {
        return runRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("部署执行记录不存在：" + id.value()));
    }

    public DeploymentRun get(DeploymentRunId id, UserId ownerId) {
        return runRepository.findByIdOwned(id, ownerId)
                .orElseThrow(() -> new EntityNotFoundException("部署执行记录不存在：" + id.value()));
    }

    public String log(DeploymentRunId id) {
        get(id);
        return logStore.read(id);
    }
}
