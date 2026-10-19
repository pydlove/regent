package com.pydlove.regent.testing.application.query;

import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.shared.domain.EntityNotFoundException;
import com.pydlove.regent.testing.domain.model.TestCaseId;
import com.pydlove.regent.testing.domain.model.TestCaseRun;
import com.pydlove.regent.testing.domain.model.TestCaseRunId;
import com.pydlove.regent.testing.domain.repository.TestCaseRunRepository;

import java.util.List;

/** 测试执行记录查询。 */
public class TestCaseRunQueryService {

    private final TestCaseRunRepository runRepository;

    public TestCaseRunQueryService(TestCaseRunRepository runRepository) {
        this.runRepository = runRepository;
    }

    public List<TestCaseRun> listByTask(TaskId taskId) {
        return runRepository.findByTask(taskId);
    }

    public List<TestCaseRun> listByTestCase(TestCaseId testCaseId, int limit) {
        return runRepository.findByTestCase(testCaseId, Math.max(1, Math.min(limit, 100)));
    }

    public TestCaseRun get(TestCaseRunId id) {
        return runRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("测试执行记录不存在：" + id.value()));
    }

    public TestCaseRun get(TestCaseRunId id, UserId ownerId) {
        return runRepository.findByIdOwned(id, ownerId)
                .orElseThrow(() -> new EntityNotFoundException("测试执行记录不存在：" + id.value()));
    }
}
