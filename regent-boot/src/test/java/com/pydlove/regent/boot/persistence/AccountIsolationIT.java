package com.pydlove.regent.boot.persistence;

import com.pydlove.regent.bug.domain.repository.BugQuery;
import com.pydlove.regent.bug.domain.repository.BugQueryRepository;
import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.repository.TaskQuery;
import com.pydlove.regent.execution.domain.repository.TaskQueryRepository;
import com.pydlove.regent.execution.domain.repository.TaskRepository;
import com.pydlove.regent.governance.domain.repository.DecisionQuery;
import com.pydlove.regent.governance.domain.repository.DecisionQueryRepository;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.plan.domain.repository.PlanRepository;
import com.pydlove.regent.project.application.command.ProjectRegistrationService;
import com.pydlove.regent.project.application.command.RegisterProjectCommand;
import com.pydlove.regent.project.domain.model.Project;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.repository.EnvironmentEntryRepository;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
import com.pydlove.regent.requirement.domain.model.RequirementId;
import com.pydlove.regent.requirement.domain.repository.RequirementRepository;
import com.pydlove.regent.testing.domain.repository.TestCaseRepository;
import com.pydlove.regent.shared.kernel.DecisionLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 账户数据隔离：项目是租户根，其他数据通过项目归属继承；环境与计划单独按账号过滤。 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AccountIsolationIT {

    private static final UserId USER_A = UserId.of(990_001L);
    private static final UserId USER_B = UserId.of(990_002L);
    private static final ProjectId PROJECT_A = ProjectId.of(991_001L);
    private static final ProjectId PROJECT_B = ProjectId.of(991_002L);
    private static final TaskId TASK_A = TaskId.of(992_001L);
    private static final TaskId TASK_B = TaskId.of(992_002L);
    private static final RequirementId REQUIREMENT_A = RequirementId.of(998_001L);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ProjectProfileRepository projectProfileRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private TaskQueryRepository taskQueryRepository;

    @Autowired
    private DecisionQueryRepository decisionQueryRepository;

    @Autowired
    private BugQueryRepository bugQueryRepository;

    @Autowired
    private EnvironmentEntryRepository environmentEntryRepository;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private TestCaseRepository testCaseRepository;

    @Autowired
    private RequirementRepository requirementRepository;

    @Autowired
    private ProjectRegistrationService projectRegistrationService;

    @BeforeEach
    void 准备两个账号的数据() {
        jdbcTemplate.update("""
                INSERT INTO app_user (id, username, display_name, password_hash, email, created_at)
                VALUES (?, 'isolation_a', '隔离账号 A', 'x', 'isolation-a@example.com', UTC_TIMESTAMP(3)),
                       (?, 'isolation_b', '隔离账号 B', 'x', 'isolation-b@example.com', UTC_TIMESTAMP(3))
                ON DUPLICATE KEY UPDATE display_name = VALUES(display_name)
                """, USER_A.value(), USER_B.value());
        jdbcTemplate.update("""
                INSERT INTO project (id, owner_id, code, name, path, default_decision_level,
                                     enabled, deleted, created_at, updated_at)
                VALUES (?, ?, 'isolation_a', '隔离项目 A', '/tmp/isolation-a', 'L1', 1, 0,
                        UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
                       (?, ?, 'isolation_b', '隔离项目 B', '/tmp/isolation-b', 'L1', 1, 0,
                        UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                """, PROJECT_A.value(), USER_A.value(), PROJECT_B.value(), USER_B.value());
        jdbcTemplate.update("""
                INSERT INTO task (id, project_id, source, title, prompt, status, priority,
                                  attempt_count, deleted, created_at, updated_at)
                VALUES (?, ?, 'MANUAL', '隔离验证任务 A', '隔离验证任务 A', 'PENDING', 0, 0, 0,
                        UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
                       (?, ?, 'MANUAL', '隔离验证任务 B', '隔离验证任务 B', 'PENDING', 0, 0, 0,
                        UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                """, TASK_A.value(), PROJECT_A.value(), TASK_B.value(), PROJECT_B.value());
        jdbcTemplate.update("""
                INSERT INTO requirement (id, project_id, title, raw_text, status, deleted,
                                         created_at, updated_at)
                VALUES (?, ?, '隔离验证需求 A', '共享项目需求', 'DRAFT', 0,
                        UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                """, REQUIREMENT_A.value(), PROJECT_A.value());
        jdbcTemplate.update("""
                INSERT INTO decision (id, task_id, project_id, ai_level, effective_level, status,
                                      question, reversible, raised_at, created_at, updated_at)
                VALUES (993001, ?, ?, 'L1', 'L1', 'PENDING', '隔离验证决策 A', 1,
                        UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
                       (993002, ?, ?, 'L1', 'L1', 'PENDING', '隔离验证决策 B', 1,
                        UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                """, TASK_A.value(), PROJECT_A.value(), TASK_B.value(), PROJECT_B.value());
        jdbcTemplate.update("""
                INSERT INTO bug (id, project_id, source, title, severity, status, found_at,
                                 deleted, created_at, updated_at)
                VALUES (994001, ?, 'MANUAL', '隔离验证缺陷 A', 'MAJOR', 'OPEN',
                        UTC_TIMESTAMP(3), 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
                       (994002, ?, 'MANUAL', '隔离验证缺陷 B', 'MAJOR', 'OPEN',
                        UTC_TIMESTAMP(3), 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                """, PROJECT_A.value(), PROJECT_B.value());
        jdbcTemplate.update("""
                INSERT INTO test_case (id, project_id, name, test_type, command_text, enabled, deleted,
                                       created_at, updated_at)
                VALUES (995001, ?, '隔离验证用例 A', 'WEB', 'echo a', 1, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
                       (995002, ?, '隔离验证用例 B', 'WEB', 'echo b', 1, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                """, PROJECT_A.value(), PROJECT_B.value());
        jdbcTemplate.update("""
                INSERT INTO environment_entry (id, user_id, project_id, item_key, item_value,
                                               description, secret, deleted, created_at, updated_at)
                VALUES (996001, ?, NULL, 'ENV_901', 'a', '账号 A 环境', 0, 0,
                        UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
                       (996002, ?, NULL, 'ENV_902', 'b', '账号 B 环境', 0, 0,
                        UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                """, USER_A.value(), USER_B.value());
        jdbcTemplate.update("""
                INSERT INTO plan (id, user_id, name, status, scheduled_at, created_at, updated_at)
                VALUES (997001, ?, '隔离验证计划 A', 'SCHEDULED', UTC_TIMESTAMP(3),
                        UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
                       (997002, ?, '隔离验证计划 B', 'SCHEDULED', UTC_TIMESTAMP(3),
                        UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                """, USER_A.value(), USER_B.value());
        jdbcTemplate.update("""
                INSERT INTO plan_task (plan_id, task_id, sort_order, created_at)
                VALUES (997001, ?, 0, UTC_TIMESTAMP(3)),
                       (997002, ?, 0, UTC_TIMESTAMP(3))
                """, TASK_A.value(), TASK_B.value());
    }

    @Test
    @DisplayName("项目成员可读取共享项目的任务、需求、缺陷、测试和决策，非成员仍不可见")
    void sharedProjectMemberReadsProjectData() {
        jdbcTemplate.update("""
                INSERT INTO project_member (project_id, user_id, role_code, created_at)
                VALUES (?, ?, 'QA', UTC_TIMESTAMP(3))
                """, PROJECT_A.value(), USER_B.value());

        assertTrue(projectProfileRepository.findById(PROJECT_A, USER_B).isPresent());
        assertTrue(projectProfileRepository.findAll(USER_B).stream()
                .anyMatch(profile -> profile.id().equals(PROJECT_A)));

        assertTrue(taskRepository.findByIdOwned(TASK_A, USER_B).isPresent());
        assertEquals(1, taskQueryRepository.findPage(TaskQuery.of(
                null, null, null, null, "隔离验证任务 A", 1, 20, USER_B)).total());
        assertTrue(requirementRepository.findByIdOwned(REQUIREMENT_A, USER_B).isPresent());

        assertEquals(1, decisionQueryRepository.findPage(DecisionQuery.of(
                null, "ALL", null, "隔离验证决策 A", 1, 20, USER_B)).total());
        assertEquals(1, bugQueryRepository.findPage(BugQuery.of(
                null, "ALL", null, "隔离验证缺陷 A", 1, 20, USER_B)).total());
        assertTrue(testCaseRepository.findAll(USER_B).stream()
                .anyMatch(testCase -> testCase.getName().equals("隔离验证用例 A")));
    }

    @Test
    @DisplayName("两个账号的列表、详情与统计只覆盖各自数据")
    void isolatesEveryAccountScopedView() {
        assertTrue(projectProfileRepository.findById(PROJECT_A, USER_A).isPresent());
        assertTrue(projectProfileRepository.findById(PROJECT_B, USER_A).isEmpty());
        assertTrue(projectProfileRepository.findAll(USER_A).stream()
                .anyMatch(profile -> profile.id().equals(PROJECT_A)));
        assertFalse(projectProfileRepository.findAll(USER_A).stream()
                .anyMatch(profile -> profile.id().equals(PROJECT_B)));

        assertTrue(taskRepository.findByIdOwned(TASK_A, USER_A).isPresent());
        assertTrue(taskRepository.findByIdOwned(TASK_B, USER_A).isEmpty());
        var taskPageA = taskQueryRepository.findPage(TaskQuery.of(
                null, null, null, null, "隔离验证任务", 1, 20, USER_A));
        assertEquals(1, taskPageA.total());
        assertEquals(TASK_A.value(), taskPageA.items().get(0).id());
        var taskPageB = taskQueryRepository.findPage(TaskQuery.of(
                null, null, null, null, "隔离验证任务", 1, 20, USER_B));
        assertEquals(1, taskPageB.total());
        assertEquals(TASK_B.value(), taskPageB.items().get(0).id());

        assertEquals(1, decisionQueryRepository.findPage(DecisionQuery.of(
                null, "ALL", null, "隔离验证决策", 1, 20, USER_A)).total());
        assertEquals(1, bugQueryRepository.findPage(BugQuery.of(
                null, "ALL", null, "隔离验证缺陷", 1, 20, USER_A)).total());

        assertTrue(environmentEntryRepository.findAll(USER_A).stream()
                .anyMatch(entry -> entry.getKey().equals("ENV_901")));
        assertFalse(environmentEntryRepository.findAll(USER_A).stream()
                .anyMatch(entry -> entry.getKey().equals("ENV_902")));
        assertTrue(planRepository.findAll(USER_A).stream()
                .anyMatch(plan -> plan.getName().equals("隔离验证计划 A")));
        assertFalse(planRepository.findAll(USER_A).stream()
                .anyMatch(plan -> plan.getName().equals("隔离验证计划 B")));
        assertTrue(testCaseRepository.findAll(USER_A).stream()
                .anyMatch(testCase -> testCase.getName().equals("隔离验证用例 A")));
        assertFalse(testCaseRepository.findAll(USER_A).stream()
                .anyMatch(testCase -> testCase.getName().equals("隔离验证用例 B")));
    }

    @Test
    @DisplayName("设备恢复队列只返回当前账号且依赖已完成的排队任务")
    void deviceRecoveryQueueHonoursOwnerAndDependencies() {
        Task own = taskRepository.findById(TASK_A).orElseThrow();
        own.queue();
        taskRepository.save(own);
        Task other = taskRepository.findById(TASK_B).orElseThrow();
        other.queue();
        taskRepository.save(other);

        Task blocker = Task.manual(ProjectId.of(PROJECT_A.value()), "依赖前置任务", "先完成", 0);
        taskRepository.save(blocker);
        Task dependent = Task.manual(ProjectId.of(PROJECT_A.value()), "等待依赖任务", "后完成", 0);
        taskRepository.save(dependent);
        dependent.queue();
        taskRepository.save(dependent);
        jdbcTemplate.update("""
                INSERT INTO task_dependency (task_id, depends_on_task_id, created_at)
                VALUES (?, ?, UTC_TIMESTAMP(3))
                """, dependent.getId().value(), blocker.getId().value());

        var queued = taskRepository.findQueued(USER_A, 10);
        assertTrue(queued.stream().anyMatch(task -> task.getId().equals(TASK_A)));
        assertFalse(queued.stream().anyMatch(task -> task.getId().equals(TASK_B)));
        assertFalse(queued.stream().anyMatch(task -> task.getId().equals(dependent.getId())),
                "前置任务未完成时不能把依赖任务重新投递给设备");

        blocker.dispatch("codex/dependency-blocker");
        blocker.complete();
        taskRepository.save(blocker);

        assertTrue(taskRepository.findQueued(USER_A, 10).stream()
                .anyMatch(task -> task.getId().equals(dependent.getId())));
    }

    @Test
    @DisplayName("新建项目写入当前账号，其他账号立即不可见")
    void writesOwnerWhenRegisteringProject(@TempDir Path workspace) {
        Project project = projectRegistrationService.register(new RegisterProjectCommand(
                "isolation_created_a", "隔离新建项目", workspace.toString(),
                null, null, DecisionLevel.L1), USER_A);

        assertEquals(USER_A, project.getOwnerId());
        assertEquals(USER_A.value(), jdbcTemplate.queryForObject(
                "SELECT owner_id FROM project WHERE id = ?",
                Long.class, project.getId().value()));
        assertTrue(projectProfileRepository.findById(project.getId(), USER_A).isPresent());
        assertTrue(projectProfileRepository.findById(project.getId(), USER_B).isEmpty());
    }
}
