package com.pydlove.regent.boot.persistence;

import com.pydlove.regent.execution.domain.model.TaskRun;
import com.pydlove.regent.execution.domain.repository.ExecutionControlTowerRepository;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.ProjectId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** 执行控制塔联合 SQL 的真实 MySQL 验证。 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ExecutionControlTowerPersistenceIT {

    private static final long USER_ID = 9491L;
    private static final long PROJECT_ID = 9491L;
    private static final long TASK_ID = 9491L;
    private static final long RUN_ID = 9491L;

    @Autowired
    private ExecutionControlTowerRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("控制塔联合查询返回任务运行与最近事件")
    void readsTaskExecution() {
        jdbcTemplate.update("""
                INSERT INTO app_user (id, username, password_hash, created_at)
                VALUES (?, 'control_tower_it', 'test-hash', UTC_TIMESTAMP(3))
                ON DUPLICATE KEY UPDATE password_hash = VALUES(password_hash)
                """, USER_ID);
        jdbcTemplate.update("""
                INSERT INTO project
                    (id, owner_id, code, name, path, default_decision_level, enabled,
                     deleted, created_at, updated_at)
                VALUES (?, ?, 'control_tower_it', '控制塔测试', '/tmp/control-tower-it',
                        'L1', 1, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                ON DUPLICATE KEY UPDATE owner_id = VALUES(owner_id), deleted = 0
                """, PROJECT_ID, USER_ID);
        jdbcTemplate.update("""
                INSERT INTO task
                    (id, project_id, source, title, prompt, status, priority,
                     attempt_count, deleted, created_at, updated_at)
                VALUES (?, ?, 'MANUAL', '控制塔任务', '执行测试', 'RUNNING', 0,
                        1, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                ON DUPLICATE KEY UPDATE status = VALUES(status), deleted = 0
                """, TASK_ID, PROJECT_ID);
        jdbcTemplate.update("""
                INSERT INTO task_run
                    (id, task_id, attempt_no, status, executor, agent_name, lease_expires_at,
                     started_at, created_at)
                VALUES (?, ?, 1, 'RUNNING', ?, '测试设备',
                        DATE_ADD(UTC_TIMESTAMP(3), INTERVAL 10 MINUTE),
                        UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                ON DUPLICATE KEY UPDATE status = VALUES(status), lease_expires_at = VALUES(lease_expires_at)
                """, RUN_ID, TASK_ID, TaskRun.EXECUTOR_AGENT);
        jdbcTemplate.update("""
                INSERT INTO run_event
                    (id, task_run_id, seq, event_type, payload, occurred_at, created_at)
                VALUES (9491, ?, 0, 'item.completed', JSON_OBJECT('message', '正在执行'),
                        UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                ON DUPLICATE KEY UPDATE occurred_at = VALUES(occurred_at)
                """, RUN_ID);

        var rows = repository.findEntries(UserId.of(USER_ID), ProjectId.of(PROJECT_ID), 20);

        assertEquals(1, rows.size());
        assertEquals("TASK", rows.get(0).executionType());
        assertEquals(RUN_ID, rows.get(0).runId());
        assertEquals("测试设备", rows.get(0).agentName());
        assertNotNull(rows.get(0).lastEventAt());
        assertEquals(true, rows.get(0).leaseExpiresAt().isAfter(Instant.now()));
    }
}
