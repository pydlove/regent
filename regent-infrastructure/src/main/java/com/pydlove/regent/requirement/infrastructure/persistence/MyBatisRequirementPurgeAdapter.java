package com.pydlove.regent.requirement.infrastructure.persistence;

import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.requirement.domain.model.RequirementId;
import com.pydlove.regent.requirement.domain.port.RequirementPurgePort;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/** 回收站需求永久删除的技术清理实现。 */
@Repository
public class MyBatisRequirementPurgeAdapter implements RequirementPurgePort {

    private final RequirementPurgeMapper mapper;

    public MyBatisRequirementPurgeAdapter(RequirementPurgeMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public int purge(ProjectId projectId, Collection<RequirementId> requirementIds) {
        List<Long> ids = requirementIds.stream().map(RequirementId::value).toList();
        mapper.deleteReleaseItems(projectId.value(), ids);
        mapper.deleteDeliveryGates(projectId.value(), ids);
        mapper.deleteRequirementDeliveries(projectId.value(), ids);
        mapper.detachTasks(projectId.value(), ids);
        mapper.detachBugs(projectId.value(), ids);
        return mapper.deleteRequirements(projectId.value(), ids);
    }
}
