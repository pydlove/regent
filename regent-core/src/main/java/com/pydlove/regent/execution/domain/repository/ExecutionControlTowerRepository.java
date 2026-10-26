package com.pydlove.regent.execution.domain.repository;

import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.ProjectId;

import java.util.List;

/** 执行控制塔只读端口，统一读取任务与部署的执行状态。 */
public interface ExecutionControlTowerRepository {

    List<ExecutionControlTowerRow> findEntries(UserId ownerId, ProjectId projectId, int limit);
}
