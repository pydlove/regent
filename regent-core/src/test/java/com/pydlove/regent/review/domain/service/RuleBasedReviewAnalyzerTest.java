package com.pydlove.regent.review.domain.service;

import com.pydlove.regent.review.domain.model.ReviewAnalysis;
import com.pydlove.regent.review.domain.model.ReviewAnalysisRequest;
import com.pydlove.regent.review.domain.model.ReviewChangeUnitKind;
import com.pydlove.regent.review.domain.model.ReviewDiff;
import com.pydlove.regent.review.domain.model.ReviewDiffFile;
import com.pydlove.regent.review.domain.model.ReviewFindingSeverity;
import com.pydlove.regent.review.domain.model.ReviewRiskLevel;
import com.pydlove.regent.review.domain.model.ReviewTaskId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleBasedReviewAnalyzerTest {

    private static final String HASH =
            "abcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcd";

    private final RuleBasedReviewAnalyzer analyzer = new RuleBasedReviewAnalyzer();

    @Test
    void 迁移删除与疑似凭据生成可定位阻断发现() {
        String diff = """
                diff --git a/db/migration/V43__drop.sql b/db/migration/V43__drop.sql
                new file mode 100644
                --- /dev/null
                +++ b/db/migration/V43__drop.sql
                @@ -0,0 +1,3 @@
                +ALTER TABLE task DROP COLUMN branch_name;
                +DELETE FROM task_run;
                +api_key=sk-live-secret
                """;
        ReviewAnalysis result = analyzer.analyze(request(diff, List.of(
                new ReviewDiffFile("db/migration/V43__drop.sql", 3, 0, false, false),
                new ReviewDiffFile("package-lock.json", 100, 20, false, true))));

        assertEquals(3, result.findings().stream()
                .filter(finding -> finding.getSeverity() == ReviewFindingSeverity.BLOCKER)
                .count());
        assertTrue(result.findings().stream().allMatch(finding ->
                finding.getFilePath() == null || finding.getLineStart() != null));
        assertTrue(result.changeUnits().stream().anyMatch(unit ->
                unit.getKind() == ReviewChangeUnitKind.LOCKFILE && !unit.isRequiresView()));
        assertTrue(result.changeUnits().stream().anyMatch(unit ->
                unit.getRiskLevel() == ReviewRiskLevel.HIGH && unit.isRequiresView()));
    }

    @Test
    void 生成文件和业务变更分开分组() {
        ReviewAnalysis result = analyzer.analyze(request(
                "diff --git a/generated/client.ts b/generated/client.ts\n"
                        + "new file mode 100644\n--- /dev/null\n+++ b/generated/client.ts\n"
                        + "@@ -0,0 +1 @@\n+export const client = {};\n",
                List.of(new ReviewDiffFile("generated/client.ts", 1, 0, false, true),
                        new ReviewDiffFile("regent-core/src/main/java/Task.java", 2, 1, false, false))));

        assertEquals(2, result.changeUnits().size());
        assertTrue(result.changeUnits().stream().anyMatch(unit ->
                unit.getKind() == ReviewChangeUnitKind.GENERATED));
        assertTrue(result.changeUnits().stream().anyMatch(unit ->
                unit.getKind() == ReviewChangeUnitKind.BUSINESS));
        assertFalse(result.changeUnits().stream()
                .filter(unit -> unit.getKind() == ReviewChangeUnitKind.GENERATED)
                .findFirst().orElseThrow().isRequiresView());
    }

    @Test
    void 解析关键hunk并限制在单元文件内() {
        String diff = """
                diff --git a/regent-core/src/main/java/Task.java b/regent-core/src/main/java/Task.java
                --- a/regent-core/src/main/java/Task.java
                +++ b/regent-core/src/main/java/Task.java
                @@ -10,2 +10,3 @@
                 class Task {
                +    boolean reviewed;
                 }
                """;
        ReviewAnalysis result = analyzer.analyze(request(diff,
                List.of(new ReviewDiffFile("regent-core/src/main/java/Task.java", 1, 0, false, false))));

        assertEquals(1, result.changeUnits().size());
        assertEquals(1, result.changeUnits().get(0).getKeyHunks().size());
        assertEquals(10, result.changeUnits().get(0).getKeyHunks().get(0).newStart());
    }

    @Test
    void 测试代码中的数据清理和测试令牌不生成阻断发现() {
        String diff = """
                diff --git a/regent-boot/src/test/java/ExampleIT.java b/regent-boot/src/test/java/ExampleIT.java
                --- a/regent-boot/src/test/java/ExampleIT.java
                +++ b/regent-boot/src/test/java/ExampleIT.java
                @@ -1,2 +1,3 @@
                 class ExampleIT {
                +    // DELETE FROM isolated_test_table; token=test-fixture-token
                 }
                """;
        ReviewAnalysis result = analyzer.analyze(request(diff,
                List.of(new ReviewDiffFile(
                        "regent-boot/src/test/java/ExampleIT.java", 1, 0, false, false))));

        assertTrue(result.findings().stream()
                .noneMatch(finding -> finding.getSeverity() == ReviewFindingSeverity.BLOCKER));
    }

    @Test
    void Tailwind的truncate样式类不会被误判成SQL清空表() {
        String diff = """
                diff --git a/regent-web/src/views/TasksView.vue b/regent-web/src/views/TasksView.vue
                --- a/regent-web/src/views/TasksView.vue
                +++ b/regent-web/src/views/TasksView.vue
                @@ -1,2 +1,3 @@
                 <template>
                +  <span class="truncate text-caption">任务标题</span>
                 </template>
                """;
        ReviewAnalysis result = analyzer.analyze(request(diff,
                List.of(new ReviewDiffFile("regent-web/src/views/TasksView.vue", 1, 0, false, false))));

        assertTrue(result.findings().stream()
                .noneMatch(finding -> finding.getSeverity() == ReviewFindingSeverity.BLOCKER));
    }

    private static ReviewAnalysisRequest request(String content, List<ReviewDiffFile> files) {
        ReviewDiff diff = new ReviewDiff("base", "head", true, HASH, files,
                files.stream().mapToInt(ReviewDiffFile::additions).sum(),
                files.stream().mapToInt(ReviewDiffFile::deletions).sum(),
                content, false);
        return new ReviewAnalysisRequest(ReviewTaskId.of(1), "/tmp/project", "实现审阅门禁",
                "任务完成后生成审阅事实", diff, List.of());
    }
}
