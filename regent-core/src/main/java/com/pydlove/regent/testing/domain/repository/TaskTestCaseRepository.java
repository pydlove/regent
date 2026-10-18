package com.pydlove.regent.testing.domain.repository;

import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.testing.domain.model.TestCaseBinding;

import java.util.List;

/** 任务与测试用例绑定。 */
public interface TaskTestCaseRepository {

    List<TestCaseBinding> findByTask(TaskId taskId);

    void replace(TaskId taskId, List<TestCaseBinding> bindings);
}
