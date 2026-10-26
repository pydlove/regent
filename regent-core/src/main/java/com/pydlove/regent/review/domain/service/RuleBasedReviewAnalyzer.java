package com.pydlove.regent.review.domain.service;

import com.pydlove.regent.review.domain.model.ReviewAnalysis;
import com.pydlove.regent.review.domain.model.ReviewAnalysisRequest;
import com.pydlove.regent.review.domain.model.ReviewChangeUnit;
import com.pydlove.regent.review.domain.model.ReviewChangeUnitKind;
import com.pydlove.regent.review.domain.model.ReviewDiff;
import com.pydlove.regent.review.domain.model.ReviewDiffFile;
import com.pydlove.regent.review.domain.model.ReviewEvidence;
import com.pydlove.regent.review.domain.model.ReviewFinding;
import com.pydlove.regent.review.domain.model.ReviewFindingSeverity;
import com.pydlove.regent.review.domain.model.ReviewHunk;
import com.pydlove.regent.review.domain.model.ReviewRiskLevel;
import com.pydlove.regent.review.domain.port.ReviewAnalyzer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 保守的第一版审阅分析。
 *
 * <p>它不假装理解全部业务，只负责把高风险路径、生成文件和锁文件先从主审队列中分开，
 * 并把删除、迁移和疑似新凭据变成可定位的发现。AI 适配器可以在此基础上替换解释能力，
 * 但风险规则仍然保留为失败关闭的底线。
 */
public class RuleBasedReviewAnalyzer implements ReviewAnalyzer {

    private static final Pattern SECRET = Pattern.compile(
            "(?i)(password|passwd|secret|token|api[_-]?key|private[_-]?key)\\s*[:=]\\s*\\S+");
    @Override
    public ReviewAnalysis analyze(ReviewAnalysisRequest request) {
        ReviewDiff diff = request.diff();
        Map<String, MutableUnit> grouped = new LinkedHashMap<>();
        for (ReviewDiffFile file : diff.files()) {
            Classification classification = classify(file.path());
            String module = moduleOf(file.path());
            String key = file.path();
            grouped.computeIfAbsent(key, ignored -> new MutableUnit(
                    module, classification.kind(), classification.risk())).files.add(file);
        }

        List<MutableUnit> ordered = new ArrayList<>(grouped.values());
        ordered.sort(Comparator
                .comparing(MutableUnit::risk).reversed()
                .thenComparing(MutableUnit::kind)
                .thenComparing(MutableUnit::module));

        Map<String, List<ReviewHunk>> hunks = parseHunks(diff.content());
        List<ReviewChangeUnit> units = new ArrayList<>();
        boolean singleHighRiskUnit = ordered.stream()
                .filter(unit -> unit.risk == ReviewRiskLevel.HIGH)
                .count() == 1;
        int sequence = 1;
        for (MutableUnit unit : ordered) {
            List<String> files = unit.files.stream().map(ReviewDiffFile::path).sorted().toList();
            List<ReviewHunk> keyHunks = files.stream()
                    .flatMap(path -> hunks.getOrDefault(path, List.of()).stream())
                    .limit(4)
                    .toList();
            units.add(ReviewChangeUnit.create(
                    sequence++,
                    unit.kind,
                    unit.risk,
                    titleOf(unit, request.taskTitle(), singleHighRiskUnit),
                    reasonOf(unit, request.taskTitle()),
                    decisionQuestionOf(unit),
                    unit.module,
                    files,
                    keyHunks,
                    unit.risk == ReviewRiskLevel.HIGH));
        }

        List<ReviewFinding> findings = findings(diff, request.taskId());
        return new ReviewAnalysis(units, findings, request.evidence());
    }

    private static List<ReviewFinding> findings(ReviewDiff diff, com.pydlove.regent.review.domain.model.ReviewTaskId taskId) {
        List<ReviewFinding> findings = new ArrayList<>();
        for (ReviewDiffFile file : diff.files()) {
            if (file.binary()) {
                continue;
            }
            if (isTestPath(file.path())) {
                continue;
            }
            List<DiffLine> lines = diffLines(diff.content(), file.path());
            for (DiffLine line : lines) {
                String upper = line.text().toUpperCase(Locale.ROOT);
                if (upper.startsWith("+") && isDestructiveSql(line.text())) {
                    findings.add(ReviewFinding.create(null, ReviewFindingSeverity.BLOCKER,
                            "存在不可逆数据操作",
                            "变更包含删除或清空数据的语句，历史数据可能不可恢复。",
                            file.path(), file.path(), line.number(), line.number(),
                            "补充数据备份、回填或可逆迁移方案", null, Instant.now()));
                }
                if (upper.startsWith("+") && SECRET.matcher(line.text()).find()) {
                    findings.add(ReviewFinding.create(null, ReviewFindingSeverity.BLOCKER,
                            "疑似新增明文凭据",
                            "新增行疑似包含密码、令牌或私钥，不能随代码交付。",
                            file.path(), file.path(), line.number(), line.number(),
                            "改用环境变量、密钥存储或脱敏占位值", null, Instant.now()));
                }
            }
        }
        if (findings.isEmpty() && diff.fileCount() > 0) {
            findings.add(ReviewFinding.create(null, ReviewFindingSeverity.INFO,
                    "规则预审未发现阻断问题",
                    "规则只覆盖迁移、删除和凭据等保守场景，不等同于人工验收。",
                    "审阅摘要", null, null, null,
                    "继续查看高风险变更单元和验证证据", null, Instant.now()));
        }
        return findings;
    }

    private static Classification classify(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith("package-lock.json") || lower.endsWith("pnpm-lock.yaml")
                || lower.endsWith("yarn.lock") || lower.endsWith("pom.lock")) {
            return new Classification(ReviewChangeUnitKind.LOCKFILE, ReviewRiskLevel.LOW);
        }
        if (lower.contains("/generated/") || lower.startsWith("generated/")
                || lower.contains("/dist/") || lower.startsWith("dist/")
                || lower.contains("/target/") || lower.startsWith("target/")
                || lower.endsWith(".min.js") || lower.endsWith(".min.css")) {
            return new Classification(ReviewChangeUnitKind.GENERATED, ReviewRiskLevel.LOW);
        }
        if (isHighRisk(lower)) {
            return new Classification(ReviewChangeUnitKind.BUSINESS, ReviewRiskLevel.HIGH);
        }
        if (isLowRisk(lower)) {
            return new Classification(ReviewChangeUnitKind.BUSINESS, ReviewRiskLevel.LOW);
        }
        return new Classification(ReviewChangeUnitKind.BUSINESS, ReviewRiskLevel.MEDIUM);
    }

    private static boolean isHighRisk(String path) {
        return path.contains("db/migration/")
                || path.contains("/auth")
                || path.contains("/security")
                || path.contains("permission")
                || path.contains("credential")
                || path.contains("secret")
                || path.contains("certificate")
                || path.endsWith(".pem")
                || path.endsWith(".key")
                || path.contains("deploy")
                || path.contains("/controller")
                || path.contains("/api/")
                || path.contains("/dto/");
    }

    private static boolean isLowRisk(String path) {
        return path.contains("/test/")
                || path.startsWith("test/")
                || path.endsWith(".md")
                || path.endsWith(".txt")
                || path.endsWith(".css")
                || path.endsWith(".svg");
    }

    private static boolean isTestPath(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.contains("/src/test/")
                || lower.startsWith("src/test/")
                || lower.startsWith("test/")
                || lower.contains("/tests/")
                || lower.endsWith("test.java")
                || lower.endsWith("tests.java");
    }

    private static boolean isDestructiveSql(String diffLine) {
        String sql = diffLine.replaceFirst("^\\+\\s*", "").strip();
        String upper = sql.toUpperCase(Locale.ROOT);
        return upper.startsWith("DROP TABLE ")
                || upper.startsWith("DROP COLUMN ")
                || upper.startsWith("TRUNCATE TABLE ")
                || upper.startsWith("DELETE FROM ")
                || (upper.startsWith("ALTER TABLE ") && upper.contains(" DROP COLUMN "));
    }

    private static String moduleOf(String path) {
        int slash = path.indexOf('/');
        return slash < 0 ? "项目根目录" : path.substring(0, slash);
    }

    private static String titleOf(MutableUnit unit, String taskTitle, boolean singleHighRiskUnit) {
        String fileName = fileNameOf(unit.files.get(0).path());
        return switch (unit.kind) {
            case GENERATED -> "生成代码：" + fileName;
            case LOCKFILE -> "依赖锁文件：" + fileName;
            case BINARY -> "二进制资源：" + fileName;
            case FORMATTING -> "格式化变更：" + fileName;
            case UNKNOWN -> "尚未确认意图：" + fileName;
            case BUSINESS -> switch (unit.risk) {
                case HIGH -> singleHighRiskUnit && taskTitle != null && !taskTitle.isBlank()
                        ? taskTitle.strip() + " · " + fileName
                        : "高风险变更：" + fileName;
                case MEDIUM -> "配套改动：" + fileName;
                case LOW -> "低风险调整：" + fileName;
            };
        };
    }

    private static String fileNameOf(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

    private static String reasonOf(MutableUnit unit, String taskTitle) {
        ReviewDiffFile file = unit.files.get(0);
        int changed = file.changedLines();
        if (unit.kind == ReviewChangeUnitKind.GENERATED) {
            return "这是生成代码，共变更 " + changed + " 行；通常不需要逐行人工判断，确认产物来源可信即可。";
        }
        if (unit.kind == ReviewChangeUnitKind.LOCKFILE) {
            return "这是依赖锁文件，共变更 " + changed + " 行；重点确认依赖升级是否与任务目标一致。";
        }
        String goal = taskTitle == null || taskTitle.isBlank()
                ? "任务目标"
                : "任务目标「" + taskTitle.strip() + "」";
        String impact = switch (unit.risk) {
            case HIGH -> "涉及权限、数据、接口、部署或核心行为等高风险路径";
            case MEDIUM -> "属于会影响现有业务行为的配套改动";
            case LOW -> "属于测试、文档或样式等低风险改动";
        };
        return "文件「" + file.path() + "」新增 " + file.additions() + " 行、删除 "
                + file.deletions() + " 行；" + impact + "，需结合" + goal + "判断是否符合预期。";
    }

    private static String decisionQuestionOf(MutableUnit unit) {
        return switch (unit.risk) {
            case HIGH -> "确认这项行为变化是否符合任务目标，并判断是否存在安全、数据或回归风险。";
            case MEDIUM -> "确认这项配套改动是否必要，以及是否会改变未声明的现有行为。";
            case LOW -> "确认这项低风险改动可以随本次交付合并。";
        };
    }

    private static Map<String, List<ReviewHunk>> parseHunks(String content) {
        Map<String, List<ReviewHunk>> result = new LinkedHashMap<>();
        String file = null;
        ReviewHunkBuilder current = null;
        for (String line : content.split("\\R", -1)) {
            if (line.startsWith("diff --git ")) {
                finishHunk(result, file, current);
                current = null;
                file = fileFromDiffHeader(line);
                continue;
            }
            if (line.startsWith("+++ b/")) {
                finishHunk(result, file, current);
                current = null;
                file = line.substring("+++ b/".length()).strip();
                continue;
            }
            if (line.startsWith("@@ ") && file != null) {
                finishHunk(result, file, current);
                current = hunkStart(line);
                current.file = file;
                current.lines.add(line);
                continue;
            }
            if (current != null) {
                current.lines.add(line);
            }
        }
        finishHunk(result, file, current);
        return result;
    }

    private static void finishHunk(Map<String, List<ReviewHunk>> result,
                                   String file,
                                   ReviewHunkBuilder hunk) {
        if (file == null || hunk == null || hunk.lines.size() <= 1) {
            return;
        }
        result.computeIfAbsent(file, ignored -> new ArrayList<>()).add(hunk.build());
    }

    private static String fileFromDiffHeader(String line) {
        int index = line.indexOf(" b/");
        return index < 0 ? "unknown" : line.substring(index + 3).strip();
    }

    private static ReviewHunkBuilder hunkStart(String line) {
        String[] fields = line.split(" ");
        if (fields.length < 3) {
            return new ReviewHunkBuilder(0, 0, 0, 0);
        }
        int[] old = range(fields[1].substring(1));
        int[] fresh = range(fields[2].substring(1));
        return new ReviewHunkBuilder(old[0], old[1], fresh[0], fresh[1]);
    }

    private static int[] range(String value) {
        String[] parts = value.split(",");
        int start = parse(parts[0]);
        int count = parts.length > 1 ? parse(parts[1]) : 1;
        return new int[]{start, count};
    }

    private static int parse(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static List<DiffLine> diffLines(String content, String path) {
        List<DiffLine> result = new ArrayList<>();
        String currentFile = null;
        int lineNo = 0;
        for (String line : content.split("\\R", -1)) {
            if (line.startsWith("diff --git ")) {
                currentFile = fileFromDiffHeader(line);
                continue;
            }
            if (line.startsWith("+++ b/")) {
                currentFile = line.substring("+++ b/".length()).strip();
                continue;
            }
            if (line.startsWith("@@ ")) {
                String[] fields = line.split(" ");
                lineNo = fields.length >= 3 ? range(fields[2].substring(1))[0] : 0;
                continue;
            }
            if (currentFile != null && currentFile.equals(path)) {
                result.add(new DiffLine(Math.max(1, lineNo), line));
                if (!line.startsWith("-")) {
                    lineNo++;
                }
            }
        }
        return result;
    }

    private static final class MutableUnit {
        private final String module;
        private final ReviewChangeUnitKind kind;
        private final List<ReviewDiffFile> files = new ArrayList<>();
        private ReviewRiskLevel risk;

        private MutableUnit(String module, ReviewChangeUnitKind kind, ReviewRiskLevel risk) {
            this.module = module;
            this.kind = kind;
            this.risk = risk;
        }

        private ReviewRiskLevel risk() {
            return risk;
        }

        private ReviewChangeUnitKind kind() {
            return kind;
        }

        private String module() {
            return module;
        }
    }

    private record Classification(ReviewChangeUnitKind kind, ReviewRiskLevel risk) {
    }

    private record DiffLine(int number, String text) {
    }

    private static final class ReviewHunkBuilder {
        private String file;
        private final int oldStart;
        private final int oldLines;
        private final int newStart;
        private final int newLines;
        private final List<String> lines = new ArrayList<>();

        private ReviewHunkBuilder(int oldStart, int oldLines, int newStart, int newLines) {
            this.oldStart = oldStart;
            this.oldLines = oldLines;
            this.newStart = newStart;
            this.newLines = newLines;
        }

        private ReviewHunk build() {
            return new ReviewHunk(file, oldStart, oldLines, newStart, newLines,
                    String.join("\n", lines));
        }
    }
}
