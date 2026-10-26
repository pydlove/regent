package com.pydlove.regent.requirement.domain.port;

import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.requirement.domain.model.RequirementId;

import java.util.Collection;

/** 永久清除回收站需求及其交付关联；任务和发布历史保留。 */
public interface RequirementPurgePort {

    int purge(ProjectId projectId, Collection<RequirementId> requirementIds);
}
