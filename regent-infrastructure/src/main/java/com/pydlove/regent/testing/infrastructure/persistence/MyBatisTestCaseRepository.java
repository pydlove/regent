package com.pydlove.regent.testing.infrastructure.persistence;

import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.testing.domain.model.TestCase;
import com.pydlove.regent.testing.domain.model.TestCaseId;
import com.pydlove.regent.testing.domain.repository.TestCaseRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/** {@link TestCaseRepository} 的 MyBatis 实现。 */
@Repository
public class MyBatisTestCaseRepository implements TestCaseRepository {

    private final TestCaseMapper mapper;

    public MyBatisTestCaseRepository(TestCaseMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<TestCase> findById(TestCaseId id) {
        return Optional.ofNullable(mapper.selectById(id.value()))
                .map(TestCaseDataMapper::toDomain);
    }

    @Override
    public Optional<TestCase> findByIdOwned(TestCaseId id, UserId ownerId) {
        return Optional.ofNullable(mapper.selectByIdOwned(id.value(), ownerId.value()))
                .map(TestCaseDataMapper::toDomain);
    }

    @Override
    public Optional<TestCase> findByIdForUpdate(TestCaseId id) {
        return Optional.ofNullable(mapper.selectByIdForUpdate(id.value()))
                .map(TestCaseDataMapper::toDomain);
    }

    @Override
    public List<TestCase> findAll() {
        return mapper.selectAll().stream().map(TestCaseDataMapper::toDomain).toList();
    }

    @Override
    public List<TestCase> findAll(UserId ownerId) {
        return mapper.selectAllOwned(ownerId.value()).stream()
                .map(TestCaseDataMapper::toDomain)
                .toList();
    }

    @Override
    public List<TestCase> findByProject(ProjectId projectId) {
        return mapper.selectByProject(projectId.value()).stream()
                .map(TestCaseDataMapper::toDomain)
                .toList();
    }

    @Override
    public boolean existsByName(ProjectId projectId, String name, TestCaseId excludingId) {
        return mapper.countByName(projectId.value(), name,
                excludingId == null ? null : excludingId.value()) > 0;
    }

    @Override
    public void save(TestCase testCase) {
        TestCaseData data = TestCaseDataMapper.toData(testCase);
        try {
            if (testCase.getId() == null) {
                if (mapper.insert(data) != 1) {
                    throw new DomainException("测试用例新增失败：" + testCase.getName());
                }
                testCase.assignIdentity(TestCaseId.of(data.getId()));
                return;
            }
            if (mapper.update(data) != 1) {
                throw new DomainException("测试用例更新失败或已被删除：" + testCase.getId());
            }
        } catch (DuplicateKeyException e) {
            throw new DomainException("测试用例保存失败，名称可能重复：" + testCase.getName(), e);
        }
    }

    @Override
    public void delete(TestCaseId id) {
        LocalDateTime now = LocalDateTime.ofInstant(
                Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS), ZoneOffset.UTC);
        if (mapper.softDelete(id.value(), now) != 1) {
            throw new DomainException("测试用例删除失败或已被删除：" + id);
        }
    }
}
