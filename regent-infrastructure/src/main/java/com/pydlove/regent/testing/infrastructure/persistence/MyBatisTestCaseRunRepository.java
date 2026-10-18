package com.pydlove.regent.testing.infrastructure.persistence;

import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.testing.domain.model.TestCaseId;
import com.pydlove.regent.testing.domain.model.TestCaseRun;
import com.pydlove.regent.testing.domain.model.TestCaseRunId;
import com.pydlove.regent.testing.domain.repository.TestCaseRunRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** {@link TestCaseRunRepository} 的 MyBatis 实现。 */
@Repository
public class MyBatisTestCaseRunRepository implements TestCaseRunRepository {

    private final TestCaseRunMapper mapper;

    public MyBatisTestCaseRunRepository(TestCaseRunMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<TestCaseRun> findById(TestCaseRunId id) {
        return Optional.ofNullable(mapper.selectById(id.value()))
                .map(TestCaseDataMapper::toDomain);
    }

    @Override
    public List<TestCaseRun> findByTask(TaskId taskId) {
        return mapper.selectByTask(taskId.value()).stream()
                .map(TestCaseDataMapper::toDomain)
                .toList();
    }

    @Override
    public List<TestCaseRun> findByTestCase(TestCaseId testCaseId, int limit) {
        return mapper.selectByTestCase(testCaseId.value(), limit).stream()
                .map(TestCaseDataMapper::toDomain)
                .toList();
    }

    @Override
    public List<TestCaseRun> findLatestByTestCaseIds(List<TestCaseId> testCaseIds) {
        if (testCaseIds == null || testCaseIds.isEmpty()) {
            return List.of();
        }
        return mapper.selectLatestByTestCaseIds(
                        testCaseIds.stream().map(TestCaseId::value).toList()).stream()
                .map(TestCaseDataMapper::toDomain)
                .toList();
    }

    @Override
    public List<TestCaseRun> findActive() {
        return mapper.selectActive().stream().map(TestCaseDataMapper::toDomain).toList();
    }

    @Override
    public boolean existsByTask(TaskId taskId) {
        return mapper.countByTask(taskId.value()) > 0;
    }

    @Override
    public void save(TestCaseRun run) {
        TestCaseRunData data = TestCaseDataMapper.toData(run);
        if (run.getId() == null) {
            if (mapper.insert(data) != 1) {
                throw new DomainException("测试执行记录新增失败");
            }
            run.assignIdentity(TestCaseRunId.of(data.getId()));
            return;
        }
        if (mapper.update(data) != 1) {
            throw new DomainException("测试执行记录更新失败：" + run.getId());
        }
    }
}
