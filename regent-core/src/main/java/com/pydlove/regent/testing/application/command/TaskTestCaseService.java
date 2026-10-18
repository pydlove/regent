package com.pydlove.regent.testing.application.command;

import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.model.TaskStatus;
import com.pydlove.regent.execution.domain.repository.TaskRepository;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.domain.EntityNotFoundException;
import com.pydlove.regent.shared.domain.TransactionRunner;
import com.pydlove.regent.testing.domain.model.TestCase;
import com.pydlove.regent.testing.domain.model.TestCaseBinding;
import com.pydlove.regent.testing.domain.model.TestCaseId;
import com.pydlove.regent.testing.domain.repository.TaskTestCaseRepository;
import com.pydlove.regent.testing.domain.repository.TestCaseRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** 任务与测试用例的绑定。绑定只决定「任务完成后跑什么」，不参与 codex 指令。 */
public class TaskTestCaseService {

    public static final int MAX_TEST_CASES_PER_TASK = 10;

    private final TaskRepository taskRepository;
    private final TestCaseRepository testCaseRepository;
    private final TaskTestCaseRepository taskTestCaseRepository;
    private final TransactionRunner transactionRunner;

    public TaskTestCaseService(TaskRepository taskRepository,
                               TestCaseRepository testCaseRepository,
                               TaskTestCaseRepository taskTestCaseRepository,
                               TransactionRunner transactionRunner) {
        this.taskRepository = taskRepository;
        this.testCaseRepository = testCaseRepository;
        this.taskTestCaseRepository = taskTestCaseRepository;
        this.transactionRunner = transactionRunner;
    }

    public List<TestCaseBinding> list(TaskId taskId) {
        requireTask(taskId);
        return taskTestCaseRepository.findByTask(taskId);
    }

    public List<TestCaseBinding> bind(TaskId taskId, List<TestCaseId> testCaseIds) {
        List<TestCaseId> unique = normalizeIds(testCaseIds);
        return transactionRunner.inTransaction(() -> {
            Task task = taskRepository.findByIdForUpdate(taskId)
                    .orElseThrow(() -> new EntityNotFoundException("任务不存在：" + taskId));
            requireEditable(task);

            Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
            List<TestCaseBinding> bindings = new ArrayList<>();
            for (TestCaseId testCaseId : unique) {
                TestCase testCase = testCaseRepository.findById(testCaseId)
                        .orElseThrow(() -> new EntityNotFoundException("测试用例不存在：" + testCaseId.value()));
                if (!testCase.getProjectId().equals(task.getProjectId())) {
                    throw new DomainException("只能绑定任务所属项目的测试用例：" + testCase.getName());
                }
                if (!testCase.isEnabled()) {
                    throw new DomainException("测试用例已停用，不能绑定：" + testCase.getName());
                }
                bindings.add(testCase.bind(now));
            }
            taskTestCaseRepository.replace(taskId, bindings);
            return List.copyOf(bindings);
        });
    }

    private Task requireTask(TaskId taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new EntityNotFoundException("任务不存在：" + taskId));
    }

    private static void requireEditable(Task task) {
        TaskStatus status = task.getStatus();
        if (status != TaskStatus.PENDING
                && status != TaskStatus.QUEUED
                && status != TaskStatus.FAILED
                && status != TaskStatus.CANCELLED) {
            throw new DomainException("任务开始执行后不能再修改测试用例绑定：" + status);
        }
    }

    private static List<TestCaseId> normalizeIds(List<TestCaseId> testCaseIds) {
        if (testCaseIds == null || testCaseIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<TestCaseId> unique = new LinkedHashSet<>(testCaseIds);
        if (unique.contains(null)) {
            throw new DomainException("测试用例 ID 不能为空");
        }
        if (unique.size() > MAX_TEST_CASES_PER_TASK) {
            throw new DomainException("一个任务最多绑定 " + MAX_TEST_CASES_PER_TASK + " 个测试用例");
        }
        return List.copyOf(unique);
    }
}
