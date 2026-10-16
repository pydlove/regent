package com.pydlove.regent.boot.persistence;

import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.model.TaskStatus;
import com.pydlove.regent.execution.domain.repository.TaskListItemPage;
import com.pydlove.regent.execution.domain.repository.TaskQuery;
import com.pydlove.regent.execution.domain.repository.TaskQueryRepository;
import com.pydlove.regent.execution.domain.repository.TaskRepository;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.domain.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 任务持久化的集成测试 —— 唯一能覆盖「领域对象 到 表结构」这条接缝的测试。
 *
 * <p>刻意命名为 IT 结尾而不是 Test 结尾：它需要真实 MySQL（schema regent_test），
 * 而 surefire 的默认包含规则认不出这个后缀，因此 mvn verify 在没有数据库的机器上
 * 依然可以全绿。手动跑法：mvn -pl regent-boot test -Dtest=TaskPersistenceIT
 *
 * <p>每个用例都在事务里执行并回滚，因此不会污染 regent_test（AGENTS.md 数据库铁律第 6 条）。
 *
 * <p>必须用真库的另一个理由：假仓储永远发现不了「时间往返丢精度」「DATETIME 约定存 UTC
 * 但读回来差八小时」这类问题，而那恰好是这类项目最常见的线上故障。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TaskPersistenceIT {

    private static final long PROBE_PROJECT_ID = 9999L;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private TaskQueryRepository taskQueryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void 准备探针项目() {
        jdbcTemplate.update("""
                INSERT INTO project (id, code, name, path, default_decision_level, enabled, deleted, created_at, updated_at)
                VALUES (?, 'it_probe', 'IT 探针项目', '/tmp/it', 'L1', 1, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                ON DUPLICATE KEY UPDATE name = VALUES(name)
                """, PROBE_PROJECT_ID);
    }

    @Test
    @DisplayName("新增后回填主键，再查回来字段一致")
    void 往返一致() {
        Task task = Task.manual(ProjectId.of(PROBE_PROJECT_ID), "IT 探针任务", "验证往返", 7);

        taskRepository.save(task);
        Task loaded = taskRepository.findById(task.getId()).orElseThrow();

        assertNotNull(task.getId(), "insert 后必须回填数据库生成的主键");
        assertEquals("IT 探针任务", loaded.getTitle());
        assertEquals(TaskStatus.PENDING, loaded.getStatus());
        assertEquals(7, loaded.getPriority());
        // DATETIME(3) 只到毫秒，入库时纳秒部分被截断，比较前拉齐精度
        assertEquals(task.getCreatedAt().truncatedTo(ChronoUnit.MILLIS),
                loaded.getCreatedAt().truncatedTo(ChronoUnit.MILLIS));
    }

    @Test
    @DisplayName("状态迁移后更新，重新查回是新状态")
    void 更新状态() {
        Task task = Task.manual(ProjectId.of(PROBE_PROJECT_ID), "IT 状态任务", "验证更新", 0);
        taskRepository.save(task);

        task.dispatch("codex/it-probe");
        taskRepository.save(task);
        Task loaded = taskRepository.findById(task.getId()).orElseThrow();

        assertEquals(TaskStatus.RUNNING, loaded.getStatus());
        assertEquals("codex/it-probe", loaded.getBranchName().orElseThrow());
        assertEquals(1, loaded.getAttemptCount());
        assertTrue(loaded.getStartedAt().isPresent());
    }

    @Test
    @DisplayName("列表按关键词筛选并正确分页")
    void 分页与筛选() {
        Task first = null;
        for (int i = 0; i < 3; i++) {
            Task saved = Task.manual(ProjectId.of(PROBE_PROJECT_ID), "IT 分页任务 " + i, "指令", 0);
            taskRepository.save(saved);
            if (i == 2) {
                first = saved;
            }
        }

        TaskListItemPage page = taskQueryRepository.findPage(new TaskQuery(null, null, "IT 分页任务", 1, 2));

        assertEquals(3, page.total(), "total 是满足条件的总行数，不是本页行数");
        assertEquals(2, page.items().size());
        assertEquals(2, page.totalPages());
        // 同优先级下按 id 倒序，最后插入的那条应排在第一页第一位
        assertEquals(first.getId().value(), page.items().get(0).id());
    }

    @Test
    @DisplayName("按项目筛选：只出这个项目的任务，条件与关键词正交")
    void 按项目筛选() {
        long otherProjectId = PROBE_PROJECT_ID + 1;
        jdbcTemplate.update("""
                INSERT INTO project (id, code, name, path, default_decision_level, enabled, deleted, created_at, updated_at)
                VALUES (?, 'it_probe_other', 'IT 另一个项目', '/tmp/it-other', 'L1', 1, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                ON DUPLICATE KEY UPDATE name = VALUES(name)
                """, otherProjectId);
        Task mine = Task.manual(ProjectId.of(PROBE_PROJECT_ID), "IT 项目筛选 A", "指令", 0);
        Task theirs = Task.manual(ProjectId.of(otherProjectId), "IT 项目筛选 B", "指令", 0);
        taskRepository.save(mine);
        taskRepository.save(theirs);

        TaskListItemPage page = taskQueryRepository.findPage(
                new TaskQuery(null, ProjectId.of(PROBE_PROJECT_ID), "IT 项目筛选", 1, 20));

        assertEquals(1, page.total(), "另一个项目的任务不该混进来");
        assertEquals(mine.getId().value(), page.items().get(0).id());
    }

    @Test
    @DisplayName("状态分布只统计未删除的任务")
    void 状态分布() {
        taskRepository.save(Task.manual(ProjectId.of(PROBE_PROJECT_ID), "IT 统计任务", "指令", 0));

        Map<TaskStatus, Long> counts = taskQueryRepository.countGroupByStatus();

        assertTrue(counts.containsKey(TaskStatus.PENDING));
        assertTrue(counts.get(TaskStatus.PENDING) >= 1);
    }

    @Test
    @DisplayName("关键词里的通配符按字面匹配，不会退化成全表")
    void 关键词转义() {
        taskRepository.save(Task.manual(ProjectId.of(PROBE_PROJECT_ID), "IT 100% 覆盖", "指令", 0));
        taskRepository.save(Task.manual(ProjectId.of(PROBE_PROJECT_ID), "IT 普通任务", "指令", 0));

        TaskListItemPage page = taskQueryRepository.findPage(new TaskQuery(null, null, "%", 1, 20));

        assertEquals(1, page.total(), "搜 % 只应命中标题里真的含 % 的那一条");
        assertTrue(page.items().get(0).title().contains("100%"));
    }

    @Test
    @DisplayName("库里不存在的任务无法更新")
    void 不存在的任务更新失败() {
        Task ghost = Task.manual(ProjectId.of(PROBE_PROJECT_ID), "IT 幽灵任务", "指令", 0);
        ghost.assignIdentity(TaskId.of(999_999L));

        assertThrows(DomainException.class, () -> taskRepository.save(ghost));
    }
}
