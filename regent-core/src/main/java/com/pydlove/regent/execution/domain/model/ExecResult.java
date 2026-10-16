package com.pydlove.regent.execution.domain.model;

import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.kernel.TokenUsage;

import java.util.List;
import java.util.Objects;

/**
 * 一次执行的终局结果 —— 适配器翻译完 codex 出参后交给应用层的形状。
 *
 * <p>{@code status} 不允许是 {@link RunStatus#RUNNING}：RUNNING 描述的是「进程还活着」，
 * 而本对象只在进程结束、出参解析完之后才产生。让 RUNNING 出现在这里，调用方就得处理
 * 一个「结果等于没有结果」的分支，而那个分支永远不该被走到。
 *
 * @param status       终局状态
 * @param exitCode     进程退出码；超时被杀时可能为 null
 * @param threadId     本次会话 ID，取自 {@code thread.started}，续跑靠它
 * @param tokens       token 消耗，取最后一次 {@code turn.completed}
 * @param summary      结构化出参里的摘要；出参不合法时为 null
 * @param rawResult    末条消息原文，复盘用
 * @param schemaValid  出参是否按约定解析成功；false 时调用方应当按失败处理
 * @param changedFiles 本次改动的文件（相对项目根），用于路径通配规则
 * @param bugs         执行器发现的缺陷；没有发现时为空列表
 * @param decision     执行器要求决策时非空
 */
public record ExecResult(
        RunStatus status,
        Integer exitCode,
        String threadId,
        TokenUsage tokens,
        String summary,
        String rawResult,
        boolean schemaValid,
        List<String> changedFiles,
        List<ReportedBug> bugs,
        ExecDecision decision) {

    public ExecResult {
        Objects.requireNonNull(status, "执行状态不能为空");
        if (status == RunStatus.RUNNING) {
            throw new DomainException("执行结果不能是 RUNNING：RUNNING 只用于描述正在跑的记录");
        }
        tokens = tokens == null ? TokenUsage.ZERO : tokens;
        changedFiles = changedFiles == null ? List.of() : List.copyOf(changedFiles);
        bugs = bugs == null ? List.of() : List.copyOf(bugs);
        if (status == RunStatus.BLOCKED && decision == null) {
            throw new DomainException("阻塞的执行结果必须带上待决策问题，否则任务会被挂起却无人知道要批什么");
        }
    }

    /**
     * 出参不合法时的失败结果。
     *
     * <p>原因写进 {@code summary} 而不是留空：任务卡在「出参读不懂」时，
     * 界面上唯一能给出来的解释就是这句话，空摘要等于让人去翻服务端日志。
     */
    public static ExecResult invalidOutput(Integer exitCode, String threadId, TokenUsage tokens,
                                           String reason, String rawResult) {
        return new ExecResult(RunStatus.FAILED, exitCode, threadId, tokens, reason, rawResult, false,
                List.of(), List.of(), null);
    }
}
