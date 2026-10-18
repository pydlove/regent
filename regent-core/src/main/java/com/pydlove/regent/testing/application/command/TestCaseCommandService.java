package com.pydlove.regent.testing.application.command;

import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.domain.EntityNotFoundException;
import com.pydlove.regent.shared.domain.TransactionRunner;
import com.pydlove.regent.testing.domain.model.TestCase;
import com.pydlove.regent.testing.domain.model.TestCaseId;
import com.pydlove.regent.testing.domain.model.TestCaseType;
import com.pydlove.regent.testing.domain.repository.TestCaseRepository;

/** 测试用例写侧用例。 */
public class TestCaseCommandService {

    private final TestCaseRepository testCaseRepository;
    private final ProjectProfileRepository projectProfileRepository;
    private final TransactionRunner transactionRunner;

    public TestCaseCommandService(TestCaseRepository testCaseRepository,
                                  ProjectProfileRepository projectProfileRepository,
                                  TransactionRunner transactionRunner) {
        this.testCaseRepository = testCaseRepository;
        this.projectProfileRepository = projectProfileRepository;
        this.transactionRunner = transactionRunner;
    }

    public TestCase create(SaveTestCaseCommand command) {
        return transactionRunner.inTransaction(() -> {
            ProjectId projectId = ProjectId.of(requireProjectId(command.projectId()));
            requireProject(projectId);
            TestCase testCase = TestCase.create(
                    projectId,
                    command.name(),
                    TestCaseType.parse(command.type()),
                    command.description(),
                    command.command(),
                    command.scriptFilename(),
                    command.scriptContent(),
                    command.enabled() == null || command.enabled());
            requireNameAvailable(projectId, testCase.getName(), null);
            testCaseRepository.save(testCase);
            return testCase;
        });
    }

    public TestCase update(TestCaseId id, SaveTestCaseCommand command) {
        return transactionRunner.inTransaction(() -> {
            TestCase testCase = testCaseRepository.findByIdForUpdate(id)
                    .orElseThrow(() -> new EntityNotFoundException("测试用例不存在：" + id));
            if (command.projectId() != null
                    && command.projectId() != testCase.getProjectId().value()) {
                throw new DomainException("测试用例不能跨项目迁移");
            }
            String nextName = command.name() == null ? testCase.getName() : command.name();
            requireNameAvailable(testCase.getProjectId(), nextName, id);
            testCase.update(
                    nextName,
                    command.type() == null ? testCase.getType() : TestCaseType.parse(command.type()),
                    command.description(),
                    command.command(),
                    command.scriptFilename(),
                    command.scriptContent(),
                    command.enabled() == null ? testCase.isEnabled() : command.enabled());
            testCaseRepository.save(testCase);
            return testCase;
        });
    }

    public TestCase setEnabled(TestCaseId id, boolean enabled) {
        return transactionRunner.inTransaction(() -> {
            TestCase testCase = testCaseRepository.findByIdForUpdate(id)
                    .orElseThrow(() -> new EntityNotFoundException("测试用例不存在：" + id));
            testCase.changeEnabled(enabled);
            testCaseRepository.save(testCase);
            return testCase;
        });
    }

    public void delete(TestCaseId id) {
        transactionRunner.inTransaction(() -> {
            testCaseRepository.findByIdForUpdate(id)
                    .orElseThrow(() -> new EntityNotFoundException("测试用例不存在：" + id));
            testCaseRepository.delete(id);
            return null;
        });
    }

    private void requireProject(ProjectId projectId) {
        projectProfileRepository.findById(projectId)
                .orElseThrow(() -> new EntityNotFoundException("项目不存在：" + projectId.value()));
    }

    private void requireNameAvailable(ProjectId projectId, String name, TestCaseId excludingId) {
        if (testCaseRepository.existsByName(projectId, name, excludingId)) {
            throw new DomainException("同一项目下已存在同名测试用例：" + name);
        }
    }

    private static long requireProjectId(Long projectId) {
        if (projectId == null) {
            throw new DomainException("必须指定所属项目");
        }
        return projectId;
    }
}
