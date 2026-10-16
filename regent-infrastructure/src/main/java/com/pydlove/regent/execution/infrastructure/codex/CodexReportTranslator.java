package com.pydlove.regent.execution.infrastructure.codex;

import com.pydlove.regent.execution.domain.model.ExecDecision;
import com.pydlove.regent.execution.domain.model.ExecResult;
import com.pydlove.regent.execution.domain.model.RunStatus;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.kernel.DecisionLevel;
import com.pydlove.regent.shared.kernel.TokenUsage;

import java.util.List;

/**
 * 结构化出参 → 平台结果。外部契约与领域模型之间唯一的翻译点。
 *
 * <p>三个约定在这里被固定下来：
 * <ol>
 *   <li>codex 的 {@code DONE} / {@code BLOCKED} / {@code FAILED} 映射到平台的
 *       {@code SUCCESS} / {@code BLOCKED} / {@code FAILED}，逐个显式写出，不用 {@code valueOf}；</li>
 *   <li>{@code BLOCKED} 必须带上完整的问题与等级，缺一项就按「出参不合法」判失败 ——
 *       一个没有问题的阻塞会让任务永远挂在那里，而人不知道该批什么；</li>
 *   <li>等级解析失败同样按不合法处理，绝不猜一个默认值。</li>
 * </ol>
 */
final class CodexReportTranslator {

    private CodexReportTranslator() {
    }

    static ExecResult translate(CodexJsonlParser.CodexReport report,
                                Integer exitCode,
                                String threadId,
                                TokenUsage tokens,
                                String rawResult) {
        return switch (normalize(report.status())) {
            case "DONE" -> new ExecResult(RunStatus.SUCCESS, exitCode, threadId, tokens,
                    report.summary(), rawResult, true, report.changedFiles(), null);
            case "FAILED" -> new ExecResult(RunStatus.FAILED, exitCode, threadId, tokens,
                    report.summary(), rawResult, true, report.changedFiles(), null);
            case "BLOCKED" -> blocked(report, exitCode, threadId, tokens, rawResult);
            default -> invalid(exitCode, threadId, tokens,
                    "出参的 status 无法识别：" + report.status(), rawResult);
        };
    }

    private static ExecResult blocked(CodexJsonlParser.CodexReport report,
                                      Integer exitCode,
                                      String threadId,
                                      TokenUsage tokens,
                                      String rawResult) {
        if (!report.hasDecision()) {
            return invalid(exitCode, threadId, tokens,
                    "出参说需要决策，却没有给出等级与问题：任务会挂起而人不知道要批什么", rawResult);
        }
        DecisionLevel level;
        try {
            level = DecisionLevel.parse(report.level());
        } catch (DomainException e) {
            return invalid(exitCode, threadId, tokens,
                    "决策等级无法识别：" + report.level(), rawResult);
        }
        ExecDecision decision = new ExecDecision(level, report.question(), report.options(),
                report.recommendation(), Boolean.TRUE.equals(report.reversible()), report.blastRadius());
        return new ExecResult(RunStatus.BLOCKED, exitCode, threadId, tokens, report.summary(),
                rawResult, true, report.changedFiles(), decision);
    }

    static ExecResult invalid(Integer exitCode, String threadId, TokenUsage tokens,
                              String reason, String rawResult) {
        return ExecResult.invalidOutput(exitCode, threadId, tokens, reason, rawResult);
    }

    private static String normalize(String status) {
        return status == null ? "" : status.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
