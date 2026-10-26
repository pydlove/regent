package com.pydlove.regent.boot.persistence;

import com.pydlove.regent.execution.domain.model.ExecutionDevice;
import com.pydlove.regent.execution.domain.repository.ExecutionDeviceRepository;
import com.pydlove.regent.identity.domain.model.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 执行可靠性底座的真实 MySQL 约束验证。 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ExecutionReliabilityPersistenceIT {

    private static final long USER_ID = 9492L;
    private static final long PROJECT_ID = 9492L;
    private static final long TASK_ID = 9492L;

    @Autowired
    private ExecutionDeviceRepository deviceRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("设备心跳可以幂等落库并读回")
    void persistsDeviceHeartbeat() {
        prepareProject();
        Instant firstSeen = Instant.parse("2026-10-21T01:00:00Z");
        ExecutionDevice first = device("R100B002", firstSeen);

        deviceRepository.save(first);
        deviceRepository.save(first.markClaimed(Instant.parse("2026-10-21T01:01:00Z")));

        ExecutionDevice saved = deviceRepository.findByDeviceId("it-device-9492").orElseThrow();
        assertEquals(USER_ID, saved.ownerId().value());
        assertEquals("R100B002", saved.clientRelease());
        assertEquals("it-instance-1", saved.agentInstanceId());
        assertEquals(Instant.parse("2026-10-21T01:01:00Z"), saved.lastClaimedAt());
    }

    @Test
    @DisplayName("数据库拒绝同一任务的第二条活跃执行")
    void rejectsSecondActiveRun() {
        prepareProject();
        insertRunningRun(1);

        assertThrows(DataIntegrityViolationException.class, () -> insertRunningRun(2));
    }

    private void prepareProject() {
        jdbcTemplate.update("""
                INSERT INTO app_user (id, username, display_name, password_hash, created_at)
                VALUES (?, 'execution_reliability_it', '执行可靠性测试', 'test-hash', UTC_TIMESTAMP(3))
                ON DUPLICATE KEY UPDATE password_hash = VALUES(password_hash)
                """, USER_ID);
        jdbcTemplate.update("""
                INSERT INTO project
                    (id, owner_id, code, name, path, default_decision_level, enabled,
                     deleted, created_at, updated_at)
                VALUES (?, ?, 'execution_reliability_it', '可靠性测试', '/tmp/execution-reliability-it',
                        'L1', 1, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                ON DUPLICATE KEY UPDATE owner_id = VALUES(owner_id), deleted = 0
                """, PROJECT_ID, USER_ID);
        jdbcTemplate.update("""
                INSERT INTO task
                    (id, project_id, source, title, prompt, status, priority,
                     attempt_count, deleted, created_at, updated_at)
                VALUES (?, ?, 'MANUAL', '活跃执行唯一性', '测试', 'RUNNING', 0,
                        2, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                ON DUPLICATE KEY UPDATE status = VALUES(status), deleted = 0
                """, TASK_ID, PROJECT_ID);
        jdbcTemplate.update("DELETE FROM task_run WHERE task_id = ?", TASK_ID);
    }

    private void insertRunningRun(int attemptNo) {
        jdbcTemplate.update("""
                INSERT INTO task_run
                    (task_id, attempt_no, status, executor, agent_name, device_id,
                     lease_expires_at, last_heartbeat_at, started_at, created_at)
                VALUES (?, ?, 'RUNNING', 'agent', 'it-device', 'it-device-9492',
                        DATE_ADD(UTC_TIMESTAMP(3), INTERVAL 10 MINUTE),
                        UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                """, TASK_ID, attemptNo);
    }

    private static ExecutionDevice device(String release, Instant seenAt) {
        return ExecutionDevice.heartbeat(
                null,
                UserId.of(USER_ID),
                "it-device-9492",
                "it-macbook",
                "darwin",
                "regent-device@it",
                "it-instance-1",
                new ExecutionDevice.CodexStatus(
                        true,
                        "/usr/local/bin/codex",
                        "codex-cli 1.0",
                        "Codex 可执行文件可用",
                        seenAt),
                release,
                "R100B002",
                seenAt);
    }
}
