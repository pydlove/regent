package com.pydlove.regent.requirement.domain.port;

import com.pydlove.regent.requirement.domain.model.RequirementIdeaDraft;
import com.pydlove.regent.requirement.domain.model.RequirementIdeaRequest;

import java.util.List;

/** 把一段产品想法整理为可编辑的需求草稿。 */
public interface RequirementIdeaDesigner {

    List<RequirementIdeaDraft> design(RequirementIdeaRequest request);
}
