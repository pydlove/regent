package com.pydlove.regent.execution.domain.repository;

import com.pydlove.regent.execution.domain.model.CodeStandard;
import com.pydlove.regent.execution.domain.model.CodeStandardId;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.ProjectId;

import java.util.List;
import java.util.Optional;

/** 项目开发规范条目仓储。 */
public interface CodeStandardRepository {

    CodeStandard save(CodeStandard standard);

    Optional<CodeStandard> findById(CodeStandardId id);

    Optional<CodeStandard> findByIdOwned(CodeStandardId id, UserId ownerId);

    List<CodeStandard> findByProject(ProjectId projectId);

    Optional<CodeStandard> findByProjectAndName(ProjectId projectId, String name);
}
