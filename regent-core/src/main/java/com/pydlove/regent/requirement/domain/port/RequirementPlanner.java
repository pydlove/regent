package com.pydlove.regent.requirement.domain.port;

import com.pydlove.regent.requirement.domain.model.RequirementPlanningRequest;
import com.pydlove.regent.requirement.domain.model.RequirementPlanningResult;

/** 通过当前执行设备上的 Codex 分析需求并生成任务草稿。 */
public interface RequirementPlanner {

    RequirementPlanningResult analyze(RequirementPlanningRequest request);
}
