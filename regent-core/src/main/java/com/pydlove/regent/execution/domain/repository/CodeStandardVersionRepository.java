package com.pydlove.regent.execution.domain.repository;

import com.pydlove.regent.execution.domain.model.CodeStandardId;
import com.pydlove.regent.execution.domain.model.CodeStandardVersion;
import com.pydlove.regent.execution.domain.model.CodeStandardVersionId;
import com.pydlove.regent.identity.domain.model.UserId;

import java.util.List;
import java.util.Optional;

/** 开发规范不可变版本仓储。 */
public interface CodeStandardVersionRepository {

    CodeStandardVersion save(CodeStandardVersion version);

    Optional<CodeStandardVersion> findById(CodeStandardVersionId id);

    Optional<CodeStandardVersion> findByIdOwned(CodeStandardVersionId id, UserId ownerId);

    Optional<CodeStandardVersion> findCurrent(CodeStandardId standardId);

    List<CodeStandardVersion> findByStandard(CodeStandardId standardId);

    int nextVersionNo(CodeStandardId standardId);
}
