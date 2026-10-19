package com.pydlove.regent.execution.infrastructure.codex;

import com.pydlove.regent.execution.domain.model.ExecReport;
import com.pydlove.regent.execution.domain.model.ExecResult;
import com.pydlove.regent.execution.domain.model.ReportedBug;
import com.pydlove.regent.shared.kernel.TokenUsage;

import java.util.List;

/**
 * codex 出参 → 平台结果。只做一件事：把 codex 那份带 {@code Boolean reversible} 的报告
 * 对齐成 {@link ExecReport}，剩下的判定（三条结局、BLOCKED 必须有等级与问题、等级解析）
 * 全在领域侧的 {@link ExecReport#toResult} 里。
 *
 * <p>为什么只留这么薄一层：同一套判定还要服务外部执行者的 {@code POST /api/agent/runs/{id}/report}。
 * 两份实现迟早会分叉，而分叉的地方正好是「该不该问人」这条线。
 */
final class CodexReportTranslator {

    private CodexReportTranslator() {
    }

    static ExecResult translate(CodexJsonlParser.CodexReport report,
                                Integer exitCode,
                                String threadId,
                                TokenUsage tokens,
                                String rawResult) {
        return ExecReport.of(report.status(), report.summary(), report.changedFiles(),
                        decisionOf(report), bugsOf(report),
                        new ExecReport.Branch(report.branchRequested(), report.branchName(),
                                report.branchReason()))
                .toResult(exitCode, threadId, tokens, rawResult);
    }

    static ExecResult invalid(Integer exitCode, String threadId, TokenUsage tokens,
                              String reason, String rawResult) {
        return ExecResult.invalidOutput(exitCode, threadId, tokens, reason, rawResult);
    }

    /** 等级与问题缺一项就当「没有决策块」：缺胳膊少腿的决策让任务挂起时，人不知道该批什么。 */
    private static ExecReport.Decision decisionOf(CodexJsonlParser.CodexReport report) {
        if (report.level() == null || report.level().isBlank()
                || report.question() == null || report.question().isBlank()) {
            return null;
        }
        return new ExecReport.Decision(report.level(), report.question(), report.options(),
                report.recommendation(), Boolean.TRUE.equals(report.reversible()), report.blastRadius());
    }

    private static List<ReportedBug> bugsOf(CodexJsonlParser.CodexReport report) {
        return report.bugs().stream()
                .map(bug -> new ReportedBug(
                        bug.title(), bug.severity(), bug.reproduction(), bug.filePath(), bug.detail()))
                .toList();
    }
}
