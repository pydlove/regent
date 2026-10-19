package com.pydlove.regent.testing.domain.repository;

import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.testing.domain.model.TestCaseId;
import com.pydlove.regent.testing.domain.model.TestCaseRun;
import com.pydlove.regent.testing.domain.model.TestCaseRunId;

import java.util.List;
import java.util.Optional;

/** 测试执行记录仓储。 */
public interface TestCaseRunRepository {

    Optional<TestCaseRun> findById(TestCaseRunId id);

    default Optional<TestCaseRun> findByIdOwned(TestCaseRunId id, UserId ownerId) {
        return findById(id);
    }

    List<TestCaseRun> findByTask(TaskId taskId);

    List<TestCaseRun> findByTestCase(TestCaseId testCaseId, int limit);

    List<TestCaseRun> findLatestByTestCaseIds(List<TestCaseId> testCaseIds);

    List<TestCaseRun> findActive();

    boolean existsByTask(TaskId taskId);

    void save(TestCaseRun run);
}
