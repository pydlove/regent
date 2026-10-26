package com.pydlove.regent.boot.persistence;

import com.pydlove.regent.gitcommit.application.command.BeginGitCommitCommand;
import com.pydlove.regent.gitcommit.application.command.GitCommitAuditService;
import com.pydlove.regent.gitcommit.application.query.GitCommitFilter;
import com.pydlove.regent.gitcommit.application.query.GitCommitQueryService;
import com.pydlove.regent.gitcommit.domain.model.GitCommitDecisionId;
import com.pydlove.regent.gitcommit.domain.model.GitCommitOperatorId;
import com.pydlove.regent.gitcommit.domain.model.GitCommitProjectId;
import com.pydlove.regent.gitcommit.domain.model.GitCommitRecord;
import com.pydlove.regent.gitcommit.domain.model.GitCommitRecordId;
import com.pydlove.regent.gitcommit.domain.model.GitCommitSnapshotId;
import com.pydlove.regent.gitcommit.domain.model.GitCommitStatus;
import com.pydlove.regent.gitcommit.domain.model.GitCommitTaskId;
import com.pydlove.regent.gitcommit.domain.model.GitCommitTaskRunId;
import com.pydlove.regent.gitcommit.domain.repository.GitCommitRecordRepository;
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

/** Git 提交独立审计记录的真实 MySQL 验证。 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GitCommitPersistenceIT {

    private static final long USER_ID = 9681L;
    private static final long PROJECT_ID = 9682L;
    private static final long TASK_ID = 9683L;
    private static final long RUN_ID = 9684L;
    private static final long SNAPSHOT_ID = 9685L;
    private static final long DECISION_ID = 9686L;
    private static final String HASH =
            "1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef";
    private static final String COMMIT =
            "abcdef1234567890abcdef1234567890abcdef12";

    @Autowired
    private GitCommitAuditService auditService;

    @Autowired
    private GitCommitRecordRepository repository;

    @Autowired
    private GitCommitQueryService queryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void 成功提交可以持久化并按项目和任务查询() {
        seed();
        GitCommitRecord running = auditService.begin(new BeginGitCommitCommand(
                GitCommitProjectId.of(PROJECT_ID),
                GitCommitTaskId.of(TASK_ID),
                GitCommitTaskRunId.of(RUN_ID),
                GitCommitSnapshotId.of(SNAPSHOT_ID),
                GitCommitDecisionId.of(DECISION_ID),
                GitCommitOperatorId.of(USER_ID),
                "Git 提交测试项目",
                "验证 Git 提交记录",
                "/tmp/git-commit-it",
                "102008",
                "feat(102008): 验证 Git 提交记录",
                "main",
                "base",
                List.of("A.java", "B.vue")));
        assertNotNull(running.getId());
        assertEquals(GitCommitStatus.RUNNING, running.getStatus());

        GitCommitRecord succeeded = auditService.succeed(
                running.getId(), COMMIT, "main", "提交完成");

        assertEquals(GitCommitStatus.SUCCEEDED, succeeded.getStatus());
        assertEquals(COMMIT, repository.findById(running.getId()).orElseThrow().getCommitSha());
        assertTrue(repository.findSucceededBySnapshot(GitCommitSnapshotId.of(SNAPSHOT_ID)).isPresent());

        var taskRecords = queryService.findByTask(TASK_ID, USER_ID);
        assertEquals(1, taskRecords.size());
        assertEquals("feat(102008): 验证 Git 提交记录", taskRecords.get(0).message());

        var page = queryService.page(USER_ID, new GitCommitFilter(
                PROJECT_ID, TASK_ID, GitCommitStatus.SUCCEEDED,
                "102008", null, null, 1, 20));
        assertEquals(1, page.total());

        var detail = queryService.detail(running.getId().value(), USER_ID);
        assertEquals(List.of("A.java", "B.vue"), detail.filePaths());
        assertEquals(DECISION_ID, detail.reviewDecisionId());
    }

    private void seed() {
        jdbcTemplate.update("""
                INSERT INTO app_user (id, username, display_name, password_hash, created_at)
                VALUES (?, 'git_commit_it', 'Git提交测试', 'test-hash', UTC_TIMESTAMP(3))
                ON DUPLICATE KEY UPDATE display_name = VALUES(display_name)
                """, USER_ID);
        jdbcTemplate.update("""
                INSERT INTO project
                    (id, owner_id, code, name, path, default_decision_level, enabled,
                     deleted, created_at, updated_at)
                VALUES (?, ?, 'git_commit_it', 'Git 提交测试项目', '/tmp/git-commit-it',
                        'L1', 1, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                ON DUPLICATE KEY UPDATE owner_id = VALUES(owner_id), deleted = 0
                """, PROJECT_ID, USER_ID);
        jdbcTemplate.update("""
                INSERT INTO task
                    (id, project_id, source, title, prompt, status, priority,
                     attempt_count, deleted, created_at, updated_at)
                VALUES (?, ?, 'MANUAL', '验证 Git 提交记录', '提交测试', 'DONE', 0,
                        1, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                ON DUPLICATE KEY UPDATE status = VALUES(status), deleted = 0
                """, TASK_ID, PROJECT_ID);
        jdbcTemplate.update("""
                INSERT INTO task_run
                    (id, task_id, attempt_no, base_ref, status, executor, changed_files,
                     started_at, finished_at, created_at)
                VALUES (?, ?, 1, 'base', 'SUCCESS', 'codex-cli', JSON_ARRAY('A.java', 'B.vue'),
                        UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                ON DUPLICATE KEY UPDATE status = VALUES(status), base_ref = VALUES(base_ref)
                """, RUN_ID, TASK_ID);
        jdbcTemplate.update("""
                INSERT INTO review_snapshot
                    (id, task_id, task_run_id, project_id, base_ref, head_ref, git_repository,
                     diff_hash, file_count, addition_count, deletion_count, files_json, status,
                     diff_content, diff_truncated, analysis_status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'base', 'head', 1, ?, 2, 3, 1,
                        JSON_ARRAY(), 'APPROVED', 'diff', 0, 'SUCCEEDED',
                        UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                ON DUPLICATE KEY UPDATE status = VALUES(status)
                """, SNAPSHOT_ID, TASK_ID, RUN_ID, PROJECT_ID, HASH);
        jdbcTemplate.update("""
                INSERT INTO review_decision
                    (id, snapshot_id, decision_type, operator_id, created_at)
                VALUES (?, ?, 'APPROVED', ?, UTC_TIMESTAMP(3))
                ON DUPLICATE KEY UPDATE decision_type = VALUES(decision_type)
                """, DECISION_ID, SNAPSHOT_ID, USER_ID);
    }
}
