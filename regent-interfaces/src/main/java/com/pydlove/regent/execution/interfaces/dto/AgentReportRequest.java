package com.pydlove.regent.execution.interfaces.dto;

import com.pydlove.regent.execution.domain.model.ExecReport;
import com.pydlove.regent.execution.domain.model.ReportedBug;
import com.pydlove.regent.shared.kernel.TokenUsage;

import java.util.List;

/**
 * 上报请求 —— 出参 JSON 加上「谁报的」。
 *
 * <p>{@code status} / {@code summary} / {@code changedFiles} / {@code decision} / {@code bugs}
 * 五个字段与《平台上报约定》里的 Schema 逐字对应，另加三个可选字段：`sessionId`（续跑钥匙）、
 * `tokens` 与 `exitCode` / `rawResult`（复盘用，报不了就别报）。
 *
 * <p>{@link #toReport()} 是这个包的职责所在：HTTP 的松散 JSON 在这里被对齐成领域形状，
 * 之后再往下走的一切（三条结局的判定、分级）都不认识 HTTP。
 */
public record AgentReportRequest(String executor,
                                 String deviceId,
                                 String sessionId,
                                 String status,
                                 String summary,
                                 List<String> changedFiles,
                                 Decision decision,
                                 List<Bug> bugs,
                                 Tokens tokens,
                                 Integer exitCode,
                                 String rawResult) {

    public AgentReportRequest(String executor,
                              String sessionId,
                              String status,
                              String summary,
                              List<String> changedFiles,
                              Decision decision,
                              List<Bug> bugs,
                              Tokens tokens,
                              Integer exitCode,
                              String rawResult) {
        this(executor, null, sessionId, status, summary, changedFiles,
                decision, bugs, tokens, exitCode, rawResult);
    }

    public ExecReport toReport() {
        ExecReport.Decision parsed = decision == null ? null : new ExecReport.Decision(
                decision.level(), decision.question(), decision.options(),
                decision.recommendation(), Boolean.TRUE.equals(decision.reversible()),
                decision.blastRadius());
        List<ReportedBug> reportedBugs = bugs == null ? List.of() : bugs.stream()
                .map(bug -> new ReportedBug(
                        bug.title(), bug.severity(), bug.reproduction(), bug.filePath(), bug.detail()))
                .toList();
        return ExecReport.of(status, summary, changedFiles, parsed, reportedBugs);
    }

    /** 执行者自报的 token 用量。缺项按 0 算 —— 与「不要编数据」一致：不知道就留空。 */
    public TokenUsage toTokens() {
        if (tokens == null) {
            return TokenUsage.ZERO;
        }
        return TokenUsage.of(orZero(tokens.input()), orZero(tokens.cachedInput()), orZero(tokens.output()));
    }

    private static long orZero(Long value) {
        return value == null ? 0L : value;
    }

    public record Decision(String level, String question, List<String> options,
                           String recommendation, Boolean reversible, String blastRadius) {
    }

    public record Bug(String title, String severity, String reproduction,
                      String filePath, String detail) {
    }

    public record Tokens(Long input, Long cachedInput, Long output) {
    }
}
