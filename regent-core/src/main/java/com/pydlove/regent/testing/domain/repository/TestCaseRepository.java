package com.pydlove.regent.testing.domain.repository;

import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.testing.domain.model.TestCase;
import com.pydlove.regent.testing.domain.model.TestCaseId;

import java.util.List;
import java.util.Optional;

/** 测试用例聚合仓储。 */
public interface TestCaseRepository {

    Optional<TestCase> findById(TestCaseId id);

    Optional<TestCase> findByIdForUpdate(TestCaseId id);

    List<TestCase> findAll();

    List<TestCase> findByProject(ProjectId projectId);

    boolean existsByName(ProjectId projectId, String name, TestCaseId excludingId);

    void save(TestCase testCase);

    void delete(TestCaseId id);
}
