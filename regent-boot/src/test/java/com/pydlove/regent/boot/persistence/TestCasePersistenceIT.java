package com.pydlove.regent.boot.persistence;

import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.execution.domain.repository.TaskRepository;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.testing.application.command.SaveTestCaseCommand;
import com.pydlove.regent.testing.application.command.TaskTestCaseService;
import com.pydlove.regent.testing.application.command.TestCaseCommandService;
import com.pydlove.regent.testing.domain.model.TestCase;
import com.pydlove.regent.testing.domain.model.TestCaseBinding;
import com.pydlove.regent.testing.domain.model.TestCaseExecutionResult;
import com.pydlove.regent.testing.domain.model.TestCaseRun;
import com.pydlove.regent.testing.domain.model.TestCaseRunStatus;
import com.pydlove.regent.testing.domain.repository.TestCaseRepository;
import com.pydlove.regent.testing.domain.repository.TestCaseRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 测试用例、任务绑定与执行记录在真实 MySQL 上的往返验证。 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TestCasePersistenceIT {

    private static final long PROJECT_ID = 9996L;

    @Autowired
    private TestCaseCommandService commandService;

    @Autowired
    private TaskTestCaseService taskTestCaseService;

    @Autowired
    private TestCaseRepository testCaseRepository;

    @Autowired
    private TestCaseRunRepository runRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void 准备探针项目() {
        jdbcTemplate.update("""
                INSERT INTO project (id, code, name, path, default_decision_level, enabled, deleted, created_at, updated_at)
                VALUES (?, 'test_case_it_probe', '测试用例 IT 探针', '/tmp/test-case-it', 'L1', 1, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                ON DUPLICATE KEY UPDATE name = VALUES(name), deleted = 0
                """, PROJECT_ID);
    }

    @Test
    @DisplayName("测试用例上传内容、任务绑定与执行记录可以完整往返")
    void persistsCaseBindingAndRun() {
        TestCase testCase = commandService.create(new SaveTestCaseCommand(
                PROJECT_ID,
                "登录页回归",
                "WEB",
                "验证登录流程",
                null,
                "login.sh",
                "echo login-ok\n",
                true));
        Task task = Task.manual(ProjectId.of(PROJECT_ID), "实现登录", "实现登录页", 0);
        taskRepository.save(task);

        List<TestCaseBinding> bindings = taskTestCaseService.bind(
                task.getId(), List.of(testCase.getId()));
        TestCaseBinding binding = bindings.get(0);
        assertEquals("echo login-ok\n", binding.scriptContent());
        assertNotNull(binding.scriptHash());

        TestCaseRun run = TestCaseRun.queueForTask(
                task.getId(), ProjectId.of(PROJECT_ID), binding);
        runRepository.save(run);
        run.begin();
        run.finish(new TestCaseExecutionResult(
                TestCaseRunStatus.SUCCESS, 0, "测试通过", "login-ok"));
        runRepository.save(run);

        TestCaseRun manual = TestCaseRun.queueManual(ProjectId.of(PROJECT_ID), binding);
        runRepository.save(manual);
        manual.begin();
        manual.finish(new TestCaseExecutionResult(
                TestCaseRunStatus.SUCCESS, 0, "手动执行通过", "manual-ok"));
        runRepository.save(manual);

        TestCase restored = testCaseRepository.findById(testCase.getId()).orElseThrow();
        assertEquals("login.sh", restored.getScriptFilename());
        assertEquals("echo login-ok\n", restored.getScriptContent());

        List<TestCaseRun> taskRuns = runRepository.findByTask(task.getId());
        assertEquals(1, taskRuns.size());
        assertEquals(TestCaseRunStatus.SUCCESS, taskRuns.get(0).getStatus());
        assertTrue(taskRuns.get(0).getOutput().contains("login-ok"));

        List<TestCaseRun> caseRuns = runRepository.findByTestCase(testCase.getId(), 10);
        assertEquals(2, caseRuns.size());
        assertEquals(TestCaseRunStatus.SUCCESS, caseRuns.get(0).getStatus());
        assertTrue(caseRuns.get(0).getTaskId().isEmpty());
    }
}
