package com.pydlove.regent.testing.infrastructure.persistence;

import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.testing.domain.model.TestCaseBinding;
import com.pydlove.regent.testing.domain.repository.TaskTestCaseRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** {@link TaskTestCaseRepository} 的 MyBatis 实现。 */
@Repository
public class MyBatisTaskTestCaseRepository implements TaskTestCaseRepository {

    private final TestCaseMapper mapper;

    public MyBatisTaskTestCaseRepository(TestCaseMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<TestCaseBinding> findByTask(TaskId taskId) {
        return mapper.selectBindingsByTask(taskId.value()).stream()
                .map(TestCaseDataMapper::toBinding)
                .toList();
    }

    @Override
    public void replace(TaskId taskId, List<TestCaseBinding> bindings) {
        mapper.deleteBindingsByTask(taskId.value());
        for (TestCaseBinding binding : bindings) {
            mapper.insertBinding(
                    taskId.value(),
                    binding.testCaseId().value(),
                    LocalDateTime.ofInstant(binding.boundAt(), ZoneOffset.UTC));
        }
    }
}
