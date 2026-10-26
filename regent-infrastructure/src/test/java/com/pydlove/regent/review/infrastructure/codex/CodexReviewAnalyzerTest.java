package com.pydlove.regent.review.infrastructure.codex;

import com.pydlove.regent.review.domain.model.ReviewAnalysis;
import com.pydlove.regent.review.domain.model.ReviewAnalysisRequest;
import com.pydlove.regent.review.domain.model.ReviewDiff;
import com.pydlove.regent.review.domain.model.ReviewDiffFile;
import com.pydlove.regent.review.domain.model.ReviewRiskLevel;
import com.pydlove.regent.review.domain.model.ReviewTaskId;
import com.pydlove.regent.review.domain.service.RuleBasedReviewAnalyzer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodexReviewAnalyzerTest {

    @TempDir
    Path temp;

    @Test
    void AI输出补充意图与发现但规则风险不得降低() throws Exception {
        Path output = Files.createTempFile(temp, "out-", ".json");
        Path script = temp.resolve("fake-codex.sh");
        Files.writeString(script, """
                #!/bin/sh
                out=""
                while [ "$#" -gt 0 ]; do
                  if [ "$1" = "-o" ]; then out="$2"; shift 2; else shift; fi
                done
                cat > /dev/null
                cat > "$out" <<'JSON'
                {
                  "changeUnits": [{
                    "title": "调整迁移策略",
                    "reason": "删除旧列前先迁移数据",
                    "kind": "BUSINESS",
                    "riskLevel": "LOW",
                    "moduleName": "database",
                    "filePaths": ["db/migration/V43__drop.sql"],
                    "requiresView": false
                  }],
                  "findings": [{
                    "severity": "BLOCKER",
                    "title": "删除列前未回填",
                    "description": "旧数据仍依赖该列",
                    "impact": "历史数据丢失",
                    "filePath": "db/migration/V43__drop.sql",
                    "lineStart": 1,
                    "lineEnd": 1,
                    "suggestedAction": "先回填再删除"
                  }]
                }
                JSON
                """);
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwx------"));

        CodexReviewAnalyzer analyzer = new CodexReviewAnalyzer(
                script.toString(), temp.resolve("runs"), Duration.ofSeconds(10),
                new RuleBasedReviewAnalyzer());
        ReviewAnalysis result = analyzer.analyze(request());

        assertTrue(result.changeUnits().get(0).getTitle()
                .startsWith("调整迁移策略"));
        assertTrue(result.changeUnits().get(0).getDecisionQuestion()
                .contains("行为变化"));
        assertEquals(ReviewRiskLevel.HIGH, result.changeUnits().get(0).getRiskLevel());
        assertTrue(result.changeUnits().get(0).isRequiresView());
        assertTrue(result.findings().stream()
                .anyMatch(finding -> finding.getTitle().equals("删除列前未回填")));
        Files.deleteIfExists(output);
    }

    @Test
    void AI失败时退回规则预审() throws Exception {
        Path script = temp.resolve("broken-codex.sh");
        Files.writeString(script, "#!/bin/sh\nexit 7\n");
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwx------"));
        CodexReviewAnalyzer analyzer = new CodexReviewAnalyzer(
                script.toString(), temp.resolve("runs"), Duration.ofSeconds(10),
                new RuleBasedReviewAnalyzer());

        ReviewAnalysis result = analyzer.analyze(request());

        assertEquals(1, result.changeUnits().size());
        assertEquals(ReviewRiskLevel.HIGH, result.changeUnits().get(0).getRiskLevel());
    }

    private ReviewAnalysisRequest request() {
        ReviewDiff diff = new ReviewDiff("base", "head",
                true,
                "1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef",
                List.of(new ReviewDiffFile("db/migration/V43__drop.sql", 2, 0, false, false)),
                2, 0, """
                diff --git a/db/migration/V43__drop.sql b/db/migration/V43__drop.sql
                new file mode 100644
                --- /dev/null
                +++ b/db/migration/V43__drop.sql
                @@ -0,0 +1,2 @@
                +ALTER TABLE task DROP COLUMN branch_name;
                +DELETE FROM task_run;
                """, false);
        return new ReviewAnalysisRequest(ReviewTaskId.of(1), temp.toString(),
                "实现迁移", "删除旧列", diff, List.of());
    }
}
