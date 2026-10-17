package com.pydlove.regent.execution.domain.model;

import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.kernel.DecisionLevel;
import com.pydlove.regent.shared.kernel.TokenUsage;

import java.util.List;
import java.util.Locale;

/**
 * 执行器的一次上报 —— 「这一轮怎么结束的」在平台内部的统一形状。
 *
 * <p>它有两条入口：平台自己拉起的 {@code codex exec} 出参，以及外部执行者通过
 * {@code POST /api/agent/runs/{id}/report} 报上来的同一份 JSON。两条路都汇合到这里，
 * 是为了让「DONE 对应成功、BLOCKED 必须带问题、等级不认就判不合法」这几条判定
 * 只有一份实现 —— 分开写的话，人工批准与自动放行的分界线会随入口而变，
 * 而这条线正是本项目最不能出错的地方。
 *
 * <p>{@code level} 保持字符串：等级解析失败本身是一条要落进记录的结论（「决策等级无法识别：L9」），
 * 在构造阶段就抛异常会把这条结论丢掉，只剩一句没人看得懂的异常。
 *
 * <p>{@code status} 之外的字段缺失不报错：摘要可以为空、改动清单可以为空，
 * 硬要它们存在只会逼执行器编内容。真正必须成对出现的是「BLOCKED 与问题」。
 */
public record ExecReport(String status,
                         String summary,
                         List<String> changedFiles,
                         Decision decision,
                         List<ReportedBug> bugs) {

    /**
     * 上报里的决策块。字段与出参 JSON 一一对应，不在这里做二次包装。
     *
     * <p>{@code reversible} 用基本类型：缺失即 false —— 与「不可逆不许 AI 自主决断」的方向一致，
     * 报不清楚就当不可逆。
     */
    public record Decision(String level, String question, List<String> options,
                           String recommendation, boolean reversible, String blastRadius) {
    }

    public ExecReport {
        changedFiles = changedFiles == null ? List.of() : List.copyOf(changedFiles);
        bugs = bugs == null ? List.of() : List.copyOf(bugs);
    }

    public static ExecReport of(String status,
                                String summary,
                                List<String> changedFiles,
                                Decision decision,
                                List<ReportedBug> bugs) {
        return new ExecReport(status, summary, changedFiles, decision, bugs);
    }

    /**
     * 落成平台结果。三条合法结局 + 一条「读不懂」的兜底，全都写在这里。
     *
     * <p>不合法一律返回 {@link ExecResult#invalidOutput}：执行记录会被判失败、原因写进摘要，
     * 而不是抛出异常 —— 抛出会让任务停在 RUNNING 上，工作台里出现一条永远不动的记录。
     */
    public ExecResult toResult(Integer exitCode, String threadId, TokenUsage tokens, String rawResult) {
        if (hasInvalidBug()) {
            return ExecResult.invalidOutput(exitCode, threadId, tokens,
                    "bugs 中存在缺少标题或严重级的缺陷，无法自动落单", rawResult);
        }
        return switch (normalize(status)) {
            case "DONE" -> result(RunStatus.SUCCESS, exitCode, threadId, tokens, rawResult);
            case "FAILED" -> result(RunStatus.FAILED, exitCode, threadId, tokens, rawResult);
            case "BLOCKED" -> blocked(exitCode, threadId, tokens, rawResult);
            default -> ExecResult.invalidOutput(exitCode, threadId, tokens,
                    "出参的 status 无法识别：" + status, rawResult);
        };
    }

    private ExecResult result(RunStatus status, Integer exitCode, String threadId,
                              TokenUsage tokens, String rawResult) {
        return new ExecResult(status, exitCode, threadId, tokens, summary, rawResult, true,
                changedFiles, bugs, null);
    }

    private ExecResult blocked(Integer exitCode, String threadId, TokenUsage tokens, String rawResult) {
        if (decision == null) {
            return ExecResult.invalidOutput(exitCode, threadId, tokens,
                    "出参说需要决策，却没有给出等级与问题：任务会挂起而人不知道要批什么", rawResult);
        }
        DecisionLevel level;
        try {
            level = DecisionLevel.parse(decision.level());
        } catch (DomainException e) {
            return ExecResult.invalidOutput(exitCode, threadId, tokens,
                    "决策等级无法识别：" + decision.level(), rawResult);
        }
        if (decision.question() == null || decision.question().isBlank()) {
            return ExecResult.invalidOutput(exitCode, threadId, tokens,
                    "出参说需要决策，却没有给出等级与问题：任务会挂起而人不知道要批什么", rawResult);
        }
        ExecDecision parsed = new ExecDecision(level, decision.question(), decision.options(),
                decision.recommendation(), decision.reversible(), decision.blastRadius());
        return new ExecResult(RunStatus.BLOCKED, exitCode, threadId, tokens, summary, rawResult, true,
                changedFiles, bugs, parsed);
    }

    private boolean hasInvalidBug() {
        return bugs.stream().anyMatch(bug -> bug == null
                || bug.title() == null || bug.title().isBlank()
                || bug.severity() == null || bug.severity().isBlank());
    }

    private static String normalize(String status) {
        return status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
    }
}
