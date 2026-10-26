package com.pydlove.regent.review.infrastructure.codex;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pydlove.regent.review.domain.model.ReviewAnalysis;
import com.pydlove.regent.review.domain.model.ReviewAnalysisRequest;
import com.pydlove.regent.review.domain.model.ReviewChangeUnit;
import com.pydlove.regent.review.domain.model.ReviewChangeUnitKind;
import com.pydlove.regent.review.domain.model.ReviewDiffFile;
import com.pydlove.regent.review.domain.model.ReviewFinding;
import com.pydlove.regent.review.domain.model.ReviewFindingSeverity;
import com.pydlove.regent.review.domain.model.ReviewHunk;
import com.pydlove.regent.review.domain.model.ReviewRiskLevel;
import com.pydlove.regent.review.domain.port.ReviewAnalyzer;
import com.pydlove.regent.shared.domain.DomainException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 用只读 Codex 会话生成风险解释与发现。
 *
 * <p>它永远先运行保守规则预审，再用 AI 输出覆盖标题、原因和补充发现。AI 失败、输出不合法或
 * 引用了 diff 之外的文件时退回规则结果；模型不能降低规则已经判定的风险等级。
 */
public class CodexReviewAnalyzer implements ReviewAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(CodexReviewAnalyzer.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_PROMPT_DIFF_CHARS = 300_000;

    private final String command;
    private final Path workDirectory;
    private final Duration timeout;
    private final ReviewAnalyzer fallback;

    public CodexReviewAnalyzer(String command,
                               Path workDirectory,
                               Duration timeout,
                               ReviewAnalyzer fallback) {
        this.command = command;
        this.workDirectory = workDirectory;
        this.timeout = timeout;
        this.fallback = fallback;
    }

    @Override
    public ReviewAnalysis analyze(ReviewAnalysisRequest request) {
        ReviewAnalysis baseline = fallback.analyze(request);
        try {
            String output = execute(request);
            return merge(baseline, parse(output, request));
        } catch (RuntimeException e) {
            log.warn("AI 审阅不可用，退回规则预审：{}", e.getMessage());
            return baseline;
        }
    }

    private String execute(ReviewAnalysisRequest request) {
        Path directory = workDirectory.toAbsolutePath().normalize();
        try {
            Files.createDirectories(directory);
            Path promptFile = Files.createTempFile(directory, "review-prompt-", ".md");
            Path outputFile = Files.createTempFile(directory, "review-output-", ".json");
            Path logFile = Files.createTempFile(directory, "review-log-", ".txt");
            Files.writeString(promptFile, prompt(request), StandardCharsets.UTF_8);
            List<String> commandLine = List.of(
                    command,
                    "exec",
                    "-C", request.projectDirectory(),
                    "-s", "read-only",
                    "--json",
                    "-o", outputFile.toString(),
                    "-");
            ProcessBuilder builder = new ProcessBuilder(commandLine);
            builder.directory(Path.of(request.projectDirectory()).toFile());
            builder.redirectInput(promptFile.toFile());
            builder.redirectErrorStream(true);
            builder.redirectOutput(logFile.toFile());
            Process process = builder.start();
            boolean completed = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!completed) {
                process.destroyForcibly();
                throw new DomainException("AI 审阅超时");
            }
            if (process.exitValue() != 0) {
                String log = readLimited(logFile, 2_000);
                throw new DomainException("AI 审阅进程失败（退出码 " + process.exitValue() + "）：" + log);
            }
            return Files.readString(outputFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new DomainException("AI 审阅无法执行：" + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DomainException("AI 审阅被中断", e);
        }
    }

    private String prompt(ReviewAnalysisRequest request) {
        String diff = request.diff().content();
        boolean truncated = diff.length() > MAX_PROMPT_DIFF_CHARS;
        if (truncated) {
            diff = diff.substring(0, MAX_PROMPT_DIFF_CHARS);
        }
        return """
                你是 Regent 的代码变更预审员。只做审阅，不修改任何文件。

                任务标题：%s
                任务要求：
                %s

                要求：
                1. 按业务意图分组，不按文件树机械罗列。
                2. 只引用下列变更文件，不得猜测不存在的位置。
                3. 风险规则已经先行判定，AI 可以解释但不得降低风险。
                4. 阻断级发现必须带文件与行号；无法定位的问题只能输出 MINOR 或 INFO。
                5. 不把“AI 认为没问题”写成验收通过。
                6. 只输出一个 JSON 对象，不要 Markdown 代码块。

                JSON 结构：
                {
                  "changeUnits": [
                    {
                      "title": "业务标题",
                      "reason": "为什么改、影响什么",
                      "decisionQuestion": "用户需要具体确认什么",
                      "kind": "BUSINESS|GENERATED|LOCKFILE|FORMATTING|BINARY|UNKNOWN",
                      "riskLevel": "HIGH|MEDIUM|LOW",
                      "moduleName": "模块",
                      "filePaths": ["相对路径"],
                      "requiresView": true
                    }
                  ],
                  "findings": [
                    {
                      "severity": "BLOCKER|CRITICAL|MAJOR|MINOR|INFO",
                      "title": "问题标题",
                      "description": "具体问题",
                      "impact": "影响",
                      "filePath": "相对路径或 null",
                      "lineStart": 1,
                      "lineEnd": 1,
                      "suggestedAction": "建议动作"
                    }
                  ]
                }

                变更文件：
                %s

                Unified diff%s：
                %s
                """.formatted(
                request.taskTitle(),
                request.taskPrompt(),
                request.diff().files().stream().map(ReviewDiffFile::path).toList(),
                truncated ? "（已截断）" : "",
                diff);
    }

    private ReviewAnalysis parse(String output, ReviewAnalysisRequest request) {
        try {
            JsonNode root = JSON.readTree(output);
            return new ReviewAnalysis(
                    jsonUnits(root.path("changeUnits")),
                    jsonFindings(root.path("findings")),
                    List.of());
        } catch (IOException | RuntimeException e) {
            throw new DomainException("AI 审阅输出不是合法 JSON", e);
        }
    }

    private List<ReviewChangeUnit> jsonUnits(JsonNode nodes) {
        if (!nodes.isArray()) {
            return List.of();
        }
        List<ReviewChangeUnit> units = new ArrayList<>();
        int sequence = 1;
        for (JsonNode node : nodes) {
            List<String> files = strings(node.path("filePaths"));
            if (files.isEmpty()) {
                continue;
            }
            units.add(ReviewChangeUnit.create(
                    sequence++,
                    enumValue(ReviewChangeUnitKind.class, text(node, "kind"), ReviewChangeUnitKind.UNKNOWN),
                    enumValue(ReviewRiskLevel.class, text(node, "riskLevel"), ReviewRiskLevel.MEDIUM),
                    required(text(node, "title"), "AI 审阅单元标题"),
                    required(text(node, "reason"), "AI 审阅单元原因"),
                    text(node, "decisionQuestion"),
                    text(node, "moduleName"),
                    files,
                    List.of(),
                    node.path("requiresView").asBoolean(false)));
        }
        return units;
    }

    private List<ReviewFinding> jsonFindings(JsonNode nodes) {
        if (!nodes.isArray()) {
            return List.of();
        }
        List<ReviewFinding> findings = new ArrayList<>();
        for (JsonNode node : nodes) {
            try {
                String file = text(node, "filePath");
                findings.add(ReviewFinding.create(
                        null,
                        enumValue(ReviewFindingSeverity.class, text(node, "severity"),
                                ReviewFindingSeverity.INFO),
                        required(text(node, "title"), "AI 发现标题"),
                        required(text(node, "description"), "AI 发现描述"),
                        text(node, "impact"),
                        file,
                        integer(node, "lineStart"),
                        integer(node, "lineEnd"),
                        text(node, "suggestedAction"),
                        null,
                        Instant.now()));
            } catch (RuntimeException ignored) {
                // 单条无定位的阻断问题会被领域守卫拒绝，保留其它有效发现。
            }
        }
        return findings;
    }

    private ReviewAnalysis merge(ReviewAnalysis baseline, ReviewAnalysis ai) {
        if (ai.changeUnits().isEmpty() && ai.findings().isEmpty()) {
            return baseline;
        }
        Map<String, ReviewRiskLevel> ruleRisk = new HashMap<>();
        Map<String, List<ReviewHunk>> ruleHunks = new HashMap<>();
        for (ReviewChangeUnit unit : baseline.changeUnits()) {
            for (String file : unit.getFilePaths()) {
                ruleRisk.merge(file, unit.getRiskLevel(), ReviewRiskLevel::higherOf);
                ruleHunks.put(file, unit.getKeyHunks());
            }
        }
        List<ReviewChangeUnit> units = new ArrayList<>();
        int sequence = 1;
        for (ReviewChangeUnit aiUnit : ai.changeUnits()) {
            List<String> files = aiUnit.getFilePaths().stream()
                    .filter(ruleRisk::containsKey)
                    .toList();
            if (files.isEmpty()) {
                continue;
            }
            ReviewRiskLevel risk = aiUnit.getRiskLevel();
            for (String file : files) {
                risk = risk.higherOf(ruleRisk.get(file));
            }
            String decisionQuestion = aiUnit.getDecisionQuestion();
            if (decisionQuestion.equals(ReviewChangeUnit.defaultDecisionQuestion(
                    aiUnit.getRiskLevel()))) {
                decisionQuestion = ReviewChangeUnit.defaultDecisionQuestion(risk);
            }
            List<ReviewHunk> hunks = files.stream()
                    .flatMap(file -> ruleHunks.getOrDefault(file, List.of()).stream())
                    .toList();
            for (String file : files) {
                List<ReviewHunk> fileHunks = hunks.stream()
                        .filter(hunk -> file.equals(hunk.filePath()))
                        .limit(4)
                        .toList();
                units.add(ReviewChangeUnit.create(
                        sequence++,
                        aiUnit.getKind(),
                        risk,
                        aiUnit.getTitle() + " · " + fileName(file),
                        aiUnit.getReason(),
                        decisionQuestion,
                        aiUnit.getModuleName(),
                        List.of(file),
                        fileHunks,
                        aiUnit.isRequiresView() || risk == ReviewRiskLevel.HIGH));
            }
        }
        if (units.isEmpty()) {
            units = baseline.changeUnits();
        }
        List<ReviewFinding> findings = new ArrayList<>(baseline.findings());
        Set<String> seen = new HashSet<>();
        for (ReviewFinding finding : baseline.findings()) {
            seen.add(findingKey(finding));
        }
        for (ReviewFinding finding : ai.findings()) {
            if (finding.getFilePath() != null && !ruleRisk.containsKey(finding.getFilePath())) {
                continue;
            }
            if (seen.add(findingKey(finding))) {
                findings.add(finding);
            }
        }
        findings.sort(Comparator.comparing(ReviewFinding::getSeverity));
        return new ReviewAnalysis(units, findings, baseline.evidence());
    }

    private static String findingKey(ReviewFinding finding) {
        return finding.getSeverity() + "|" + finding.getFilePath() + "|"
                + finding.getLineStart() + "|" + finding.getTitle();
    }

    private static String fileName(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value, E fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value.strip().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static List<String> strings(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode value : node) {
            if (value.isTextual() && !value.asText().isBlank()) {
                values.add(value.asText().strip());
            }
        }
        return values;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() && !value.asText().isBlank() ? value.asText().strip() : null;
    }

    private static Integer integer(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isIntegralNumber() ? value.asInt() : null;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new DomainException(field + "不能为空");
        }
        return value;
    }

    private static String readLimited(Path file, int max) {
        try {
            String value = Files.readString(file, StandardCharsets.UTF_8);
            return value.length() <= max ? value : value.substring(0, max);
        } catch (IOException e) {
            return "";
        }
    }
}
