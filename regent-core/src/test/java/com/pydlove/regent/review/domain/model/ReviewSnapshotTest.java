package com.pydlove.regent.review.domain.model;

import com.pydlove.regent.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReviewSnapshotTest {

    private static final Instant NOW = Instant.parse("2026-10-21T06:00:00Z");
    private static final String HASH =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    void 创建快照固定代码事实并等待审阅() {
        ReviewSnapshot snapshot = snapshot();

        assertEquals(ReviewSnapshotStatus.WAITING_REVIEW, snapshot.getStatus());
        assertEquals(ReviewAnalysisStatus.PENDING, snapshot.getAnalysisStatus());
        assertEquals(HASH, snapshot.getDiffHash());
        assertEquals(2, snapshot.getFileCount());
        assertTrue(snapshot.matchesDiffHash(HASH));
        assertFalse(snapshot.isDeployable());
    }

    @Test
    void 高风险业务单元默认必看未查看不能通过() {
        ReviewSnapshot snapshot = snapshotWithAnalysis(
                List.of(unit(1, ReviewChangeUnitKind.BUSINESS, ReviewRiskLevel.HIGH,
                        "任务状态机", List.of("Task.java"), false)),
                List.of());

        assertTrue(snapshot.getChangeUnits().get(0).isRequiresView());
        DomainException error = assertThrows(DomainException.class,
                () -> snapshot.approve(NOW.plusSeconds(1)));
        assertTrue(error.getMessage().contains("必看"));
    }

    @Test
    void 必看单元查看并通过后可以部署() {
        ReviewSnapshot snapshot = snapshotWithAnalysis(
                List.of(unit(1, ReviewChangeUnitKind.BUSINESS, ReviewRiskLevel.HIGH,
                        "任务状态机", List.of("Task.java"), false)),
                List.of());
        ReviewChangeUnit unit = snapshot.getChangeUnits().get(0);
        unit.assignIdentity(ReviewChangeUnitId.of(11));

        assertTrue(snapshot.markViewed(unit.getId(), NOW.plusSeconds(2)));
        assertFalse(snapshot.markViewed(unit.getId(), NOW.plusSeconds(3)));
        snapshot.decideUnit(unit.getId(), ReviewUnitDecisionStatus.APPROVED,
                ReviewOperatorId.of(7), null, NOW.plusSeconds(4));
        snapshot.approve(NOW.plusSeconds(5));

        assertEquals(ReviewSnapshotStatus.APPROVED, snapshot.getStatus());
        assertTrue(snapshot.isDeployable());
        assertEquals(1, snapshot.viewedRequiredUnitCount());
    }

    @Test
    void 单个diff可以要求修改并阻断整单通过() {
        ReviewSnapshot snapshot = snapshotWithAnalysis(
                List.of(unit(1, ReviewChangeUnitKind.BUSINESS, ReviewRiskLevel.HIGH,
                        "任务状态机", List.of("Task.java"), true)),
                List.of());
        ReviewChangeUnit unit = snapshot.getChangeUnits().get(0);
        unit.assignIdentity(ReviewChangeUnitId.of(12));

        snapshot.decideUnit(unit.getId(), ReviewUnitDecisionStatus.CHANGES_REQUESTED,
                ReviewOperatorId.of(7), "请补充回归测试", NOW.plusSeconds(2));

        assertEquals(ReviewUnitDecisionStatus.CHANGES_REQUESTED, unit.getDecisionStatus());
        DomainException error = assertThrows(DomainException.class,
                () -> snapshot.approve(NOW.plusSeconds(3)));
        assertTrue(error.getMessage().contains("逐项通过"));
    }

    @Test
    void 阻断发现未处理时不能通过且误报后解除阻断() {
        ReviewSnapshot snapshot = snapshotWithAnalysis(
                List.of(unit(1, ReviewChangeUnitKind.BUSINESS, ReviewRiskLevel.MEDIUM,
                        "迁移脚本", List.of("V42__review.sql"), false)),
                List.of(finding(ReviewFindingSeverity.BLOCKER, "V42__review.sql", 8)));
        ReviewFinding finding = snapshot.getFindings().get(0);
        finding.assignIdentity(ReviewFindingId.of(21));

        DomainException error = assertThrows(DomainException.class,
                () -> snapshot.approve(NOW.plusSeconds(2)));
        assertTrue(error.getMessage().contains("阻断"));

        snapshot.markFinding(finding.getId(), ReviewFindingStatus.FALSE_POSITIVE, NOW.plusSeconds(3));
        snapshot.approve(NOW.plusSeconds(4));

        assertEquals(ReviewFindingStatus.FALSE_POSITIVE, finding.getStatus());
        assertEquals(0, snapshot.unresolvedBlockerCount());
        assertEquals(ReviewSnapshotStatus.APPROVED, snapshot.getStatus());
    }

    @Test
    void 低风险生成文件默认不进入必看队列() {
        ReviewSnapshot snapshot = snapshotWithAnalysis(
                List.of(unit(1, ReviewChangeUnitKind.GENERATED, ReviewRiskLevel.LOW,
                        "生成客户端", List.of("generated/client.ts"), false)),
                List.of());

        assertFalse(snapshot.getChangeUnits().get(0).isRequiresView());
        snapshot.approve(NOW.plusSeconds(1));
        assertEquals(ReviewSnapshotStatus.APPROVED, snapshot.getStatus());
    }

    @Test
    void 已过期快照不能再次通过() {
        ReviewSnapshot snapshot = snapshot();
        snapshot.expire(NOW.plusSeconds(1));

        assertEquals(ReviewSnapshotStatus.EXPIRED, snapshot.getStatus());
        assertThrows(DomainException.class, () -> snapshot.approve(NOW.plusSeconds(2)));
    }

    @Test
    void 阻断发现必须带文件与行号() {
        assertThrows(DomainException.class,
                () -> ReviewFinding.create(null, ReviewFindingSeverity.BLOCKER,
                        "无法定位的问题", "描述", null, null, null, null,
                        null, null, NOW));
    }

    @Test
    void 要求修改和驳回必须填写意见() {
        assertThrows(DomainException.class,
                () -> ReviewDecision.create(ReviewSnapshotId.of(1),
                        ReviewDecisionType.CHANGES_REQUESTED,
                        ReviewOperatorId.of(7), " ", NOW));
        assertThrows(DomainException.class,
                () -> ReviewDecision.create(ReviewSnapshotId.of(1),
                        ReviewDecisionType.REJECTED,
                        ReviewOperatorId.of(7), null, NOW));
    }

    @Test
    void 通过结论允许不填写意见并保留操作者() {
        ReviewDecision decision = ReviewDecision.create(ReviewSnapshotId.of(1),
                ReviewDecisionType.APPROVED, ReviewOperatorId.of(7), null, NOW);

        assertEquals(ReviewDecisionType.APPROVED, decision.getDecision());
        assertEquals(7, decision.getOperatorId().value());
        assertEquals(null, decision.getComment());
    }

    private static ReviewSnapshot snapshot() {
        ReviewDiff diff = new ReviewDiff(
                "abc123", "def456", true, HASH,
                List.of(new ReviewDiffFile("Task.java", 10, 2, false, false),
                        new ReviewDiffFile("TaskMapper.xml", 4, 1, false, false)),
                14, 3, "diff --git a/Task.java b/Task.java", false);
        return ReviewSnapshot.create(ReviewTaskId.of(1), ReviewTaskRunId.of(2),
                ReviewProjectId.of(3), diff, NOW);
    }

    private static ReviewSnapshot snapshotWithAnalysis(List<ReviewChangeUnit> units,
                                                        List<ReviewFinding> findings) {
        ReviewSnapshot snapshot = snapshot();
        snapshot.attachAnalysis(new ReviewAnalysis(units, findings, List.of()), NOW.plusSeconds(1));
        return snapshot;
    }

    private static ReviewChangeUnit unit(int sequence,
                                         ReviewChangeUnitKind kind,
                                         ReviewRiskLevel risk,
                                         String title,
                                         List<String> files,
                                         boolean requiresView) {
        return ReviewChangeUnit.create(sequence, kind, risk, title, "修改原因",
                "execution", files, List.of(), requiresView);
    }

    private static ReviewFinding finding(ReviewFindingSeverity severity,
                                         String file,
                                         int line) {
        return ReviewFinding.create(null, severity, "迁移可能丢数据",
                "没有回填旧数据", "历史记录不可恢复", file, line, line,
                "补充回填脚本", null, NOW);
    }
}
