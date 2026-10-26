package com.pydlove.regent.boot.persistence;

import com.pydlove.regent.review.domain.model.ReviewAnalysis;
import com.pydlove.regent.review.domain.model.ReviewChangeUnit;
import com.pydlove.regent.review.domain.model.ReviewChangeUnitKind;
import com.pydlove.regent.review.domain.model.ReviewDecision;
import com.pydlove.regent.review.domain.model.ReviewDecisionType;
import com.pydlove.regent.review.domain.model.ReviewDiff;
import com.pydlove.regent.review.domain.model.ReviewDiffFile;
import com.pydlove.regent.review.domain.model.ReviewEvidence;
import com.pydlove.regent.review.domain.model.ReviewEvidenceStatus;
import com.pydlove.regent.review.domain.model.ReviewEvidenceType;
import com.pydlove.regent.review.domain.model.ReviewFinding;
import com.pydlove.regent.review.domain.model.ReviewFindingSeverity;
import com.pydlove.regent.review.domain.model.ReviewFindingStatus;
import com.pydlove.regent.review.domain.model.ReviewOperatorId;
import com.pydlove.regent.review.domain.model.ReviewProjectId;
import com.pydlove.regent.review.domain.model.ReviewRiskLevel;
import com.pydlove.regent.review.domain.model.ReviewSnapshot;
import com.pydlove.regent.review.domain.model.ReviewSnapshotStatus;
import com.pydlove.regent.review.domain.model.ReviewTaskId;
import com.pydlove.regent.review.domain.model.ReviewTaskRunId;
import com.pydlove.regent.review.domain.model.ReviewUnitDecisionStatus;
import com.pydlove.regent.review.domain.repository.ReviewDecisionRepository;
import com.pydlove.regent.review.domain.repository.ReviewSnapshotRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** 审阅聚合、子实体和不可变结论的真实 MySQL 持久化验证。 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReviewPersistenceIT {

    private static final long USER_ID = 9481L;
    private static final long PROJECT_ID = 9482L;
    private static final long TASK_ID = 9483L;
    private static final long RUN_ID = 9484L;
    private static final String HASH =
            "1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef";

    @Autowired
    private ReviewSnapshotRepository snapshotRepository;

    @Autowired
    private ReviewDecisionRepository decisionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("审阅快照、分组、发现、证据和人工结论可以完整读写")
    void persistsReviewAggregate() {
        seedTaskRun();
        ReviewSnapshot snapshot = ReviewSnapshot.create(
                ReviewTaskId.of(TASK_ID),
                ReviewTaskRunId.of(RUN_ID),
                ReviewProjectId.of(PROJECT_ID),
                new ReviewDiff("base", "head", true, HASH,
                        List.of(new ReviewDiffFile("Task.java", 2, 1, false, false)),
                        2, 1, "diff --git a/Task.java b/Task.java", false),
                Instant.parse("2026-10-21T06:00:00Z"));
        ReviewChangeUnit unit = ReviewChangeUnit.create(
                1, ReviewChangeUnitKind.BUSINESS, ReviewRiskLevel.HIGH,
                "核心行为变更", "调整任务完成逻辑", "execution",
                List.of("Task.java"), List.of(), true);
        ReviewEvidence evidence = ReviewEvidence.create(
                null, ReviewEvidenceType.TEST, ReviewEvidenceStatus.PASSED,
                "单元测试", "mvn test", "通过", "TEST_CASE_RUN", 9L,
                Instant.parse("2026-10-21T06:00:30Z"));
        ReviewFinding finding = ReviewFinding.create(
                null, ReviewFindingSeverity.BLOCKER, "可能丢数据",
                "删除后没有回填", "历史数据不可恢复", "Task.java", 8, 8,
                "补充回填", null, Instant.parse("2026-10-21T06:00:40Z"));
        snapshot.attachAnalysis(new ReviewAnalysis(List.of(unit), List.of(finding), List.of(evidence)),
                Instant.parse("2026-10-21T06:01:00Z"));
        snapshotRepository.save(snapshot);

        assertNotNull(snapshot.getId());
        ReviewSnapshot restored = snapshotRepository.findById(snapshot.getId()).orElseThrow();
        assertEquals(1, restored.getChangeUnits().size());
        assertEquals(1, restored.getFindings().size());
        assertEquals(1, restored.getEvidence().size());
        assertEquals(List.of("Task.java"),
                restored.getFiles().stream().map(ReviewDiffFile::path).toList());
        assertNotNull(restored.getChangeUnits().get(0).getId());

        restored.markViewed(restored.getChangeUnits().get(0).getId(),
                Instant.parse("2026-10-21T06:02:00Z"));
        restored.markFinding(restored.getFindings().get(0).getId(),
                ReviewFindingStatus.FALSE_POSITIVE, Instant.parse("2026-10-21T06:03:00Z"));
        restored.decideUnit(restored.getChangeUnits().get(0).getId(),
                ReviewUnitDecisionStatus.APPROVED, ReviewOperatorId.of(USER_ID), null,
                Instant.parse("2026-10-21T06:03:30Z"));
        snapshotRepository.save(restored);
        ReviewSnapshot updated = snapshotRepository.findById(snapshot.getId()).orElseThrow();
        assertNotNull(updated.getChangeUnits().get(0).getViewedAt());
        assertEquals(ReviewFindingStatus.FALSE_POSITIVE, updated.getFindings().get(0).getStatus());
        assertEquals(ReviewUnitDecisionStatus.APPROVED,
                updated.getChangeUnits().get(0).getDecisionStatus());

        ReviewDecision decision = decisionRepository.save(ReviewDecision.create(
                snapshot.getId(), ReviewDecisionType.APPROVED, ReviewOperatorId.of(USER_ID),
                null, Instant.parse("2026-10-21T06:04:00Z")));
        assertNotNull(decision.getId());
        assertEquals(ReviewDecisionType.APPROVED,
                decisionRepository.findLatestBySnapshot(snapshot.getId()).orElseThrow().getDecision());
        updated.approve(Instant.parse("2026-10-21T06:05:00Z"));
        updated.markCommitted(ReviewOperatorId.of(USER_ID),
                "fedcba9876543210fedcba9876543210fedcba98",
                "feat(review): 完成审阅提交",
                Instant.parse("2026-10-21T06:06:00Z"));
        snapshotRepository.save(updated);

        ReviewSnapshot committed = snapshotRepository.findById(snapshot.getId()).orElseThrow();
        assertEquals(ReviewSnapshotStatus.COMMITTED, committed.getStatus());
        assertEquals("fedcba9876543210fedcba9876543210fedcba98", committed.getCommitSha());
        assertEquals(USER_ID, committed.getCommittedBy().value());
        assertEquals("feat(review): 完成审阅提交", committed.getCommitMessage());
    }

    private void seedTaskRun() {
        jdbcTemplate.update("""
                INSERT INTO app_user (id, username, display_name, password_hash, created_at)
                VALUES (?, 'review_it', '审阅测试', 'test-hash', UTC_TIMESTAMP(3))
                ON DUPLICATE KEY UPDATE password_hash = VALUES(password_hash)
                """, USER_ID);
        jdbcTemplate.update("""
                INSERT INTO project
                    (id, owner_id, code, name, path, default_decision_level, enabled,
                     deleted, created_at, updated_at)
                VALUES (?, ?, 'review_it', '审阅测试', '/tmp/review-it',
                        'L1', 1, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                ON DUPLICATE KEY UPDATE owner_id = VALUES(owner_id), deleted = 0
                """, PROJECT_ID, USER_ID);
        jdbcTemplate.update("""
                INSERT INTO task
                    (id, project_id, source, title, prompt, status, priority,
                     attempt_count, deleted, created_at, updated_at)
                VALUES (?, ?, 'MANUAL', '审阅任务', '完成变更', 'DONE', 0,
                        1, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                ON DUPLICATE KEY UPDATE status = VALUES(status), deleted = 0
                """, TASK_ID, PROJECT_ID);
        jdbcTemplate.update("""
                INSERT INTO task_run
                    (id, task_id, attempt_no, base_ref, status, executor, changed_files,
                     started_at, finished_at, created_at)
                VALUES (?, ?, 1, 'base', 'SUCCESS', 'codex-cli', JSON_ARRAY('Task.java'),
                        UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                ON DUPLICATE KEY UPDATE status = VALUES(status), base_ref = VALUES(base_ref)
                """, RUN_ID, TASK_ID);
    }
}
