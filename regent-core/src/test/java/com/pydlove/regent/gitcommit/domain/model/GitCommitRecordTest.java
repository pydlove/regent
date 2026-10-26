package com.pydlove.regent.gitcommit.domain.model;

import com.pydlove.regent.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GitCommitRecordTest {

    private static final Instant NOW = Instant.parse("2026-10-21T08:00:00Z");
    private static final String SHA =
            "1234567890abcdef1234567890abcdef12345678";

    @Test
    void 成功收敛后保留提交事实() {
        GitCommitRecord record = running();

        record.succeed(SHA, "main", "提交完成", NOW.plusSeconds(2));

        assertEquals(GitCommitStatus.SUCCEEDED, record.getStatus());
        assertEquals(SHA, record.getCommitSha());
        assertEquals(NOW.plusSeconds(2), record.getFinishedAt());
        assertThrows(DomainException.class,
                () -> record.fail("重复收敛", null, NOW.plusSeconds(3)));
    }

    @Test
    void 失败记录不能改写成成功() {
        GitCommitRecord record = running();
        record.fail("暂存区存在其他改动", "git status", NOW.plusSeconds(1));

        assertEquals(GitCommitStatus.FAILED, record.getStatus());
        assertThrows(DomainException.class,
                () -> record.succeed(SHA, "main", null, NOW.plusSeconds(2)));
    }

    @Test
    void Bug编号必须为六位数字() {
        assertThrows(DomainException.class, () -> GitCommitRecord.start(
                GitCommitProjectId.of(1), GitCommitTaskId.of(2), GitCommitTaskRunId.of(3),
                GitCommitSnapshotId.of(4), GitCommitDecisionId.of(5), GitCommitOperatorId.of(6),
                "Regent", "提交记录", "/tmp/regent", "12345",
                "feat(12345): 不合法", "main", "base", List.of("A.java"), NOW));
    }

    @Test
    void 未关联Bug时允许不记录编号() {
        GitCommitRecord record = GitCommitRecord.start(
                GitCommitProjectId.of(1), GitCommitTaskId.of(2), GitCommitTaskRunId.of(3),
                GitCommitSnapshotId.of(4), GitCommitDecisionId.of(5), GitCommitOperatorId.of(6),
                "Regent", "提交记录", "/tmp/regent", null,
                "feat: 提交记录", "main", "base", List.of("A.java"), NOW);

        assertNull(record.getTicket());
    }

    private static GitCommitRecord running() {
        return GitCommitRecord.start(
                GitCommitProjectId.of(1), GitCommitTaskId.of(2), GitCommitTaskRunId.of(3),
                GitCommitSnapshotId.of(4), GitCommitDecisionId.of(5), GitCommitOperatorId.of(6),
                "Regent", "提交记录", "/tmp/regent", "102008",
                "feat(102008): 提交记录", "main", "base",
                List.of("A.java"), NOW);
    }
}
