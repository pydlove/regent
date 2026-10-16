package com.pydlove.regent.execution.domain.model;

import com.pydlove.regent.shared.kernel.DecisionLevel;
import com.pydlove.regent.shared.kernel.TokenUsage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 出参翻译的守卫。它服务两条路：平台自己拉起的 codex，以及外部执行者报回来的同一份 JSON ——
 * 所以这里每一条断言都同时是「外部工具能被怎么拒绝」的说明书。
 */
class ExecReportTest {

    @Test
    @DisplayName("DONE 落成功，改动清单原样带过去")
    void done落成功() {
        ExecResult result = report("DONE", null).toResult(0, "th-1", TokenUsage.ZERO, "原文");

        assertEquals(RunStatus.SUCCESS, result.status());
        assertEquals(List.of("src/a.ts"), result.changedFiles());
        assertTrue(result.schemaValid());
    }

    @Test
    @DisplayName("FAILED 落失败，不因为「它至少报了个状态」就放行")
    void failed落失败() {
        ExecResult result = report("FAILED", null).toResult(1, null, TokenUsage.ZERO, null);

        assertEquals(RunStatus.FAILED, result.status());
        assertTrue(result.schemaValid());
    }

    @Test
    @DisplayName("BLOCKED 带完整决策：等级解析出来，问题与可选性一起带过去")
    void blocked带决策() {
        ExecResult result = report("BLOCKED", decision("L2", true)).toResult(null, null, TokenUsage.ZERO, null);

        assertEquals(RunStatus.BLOCKED, result.status());
        assertEquals(DecisionLevel.L2, result.decision().level());
        assertEquals("要不要动依赖？", result.decision().question());
        assertTrue(result.decision().reversible());
    }

    @Test
    @DisplayName("说需要决策却没有决策块：判不合法，摘要写明「人不知道该批什么」")
    void blocked缺决策块() {
        ExecResult result = report("BLOCKED", null).toResult(null, null, TokenUsage.ZERO, null);

        assertEquals(RunStatus.FAILED, result.status());
        assertFalse(result.schemaValid());
        assertTrue(result.summary().contains("人不知道要批什么"), result.summary());
    }

    @Test
    @DisplayName("等级读不懂：判不合法并原样回显，绝不猜一个默认等级")
    void 等级读不懂() {
        ExecResult result = report("BLOCKED", decision("L9", true)).toResult(null, null, TokenUsage.ZERO, null);

        assertFalse(result.schemaValid());
        assertTrue(result.summary().contains("L9"), result.summary());
    }

    @Test
    @DisplayName("问题为空也按缺决策块处理：挂起一个没人能回答的问题等于把任务弄丢")
    void 问题为空() {
        ExecResult result = report("BLOCKED",
                new ExecReport.Decision("L0", "  ", List.of(), "", true, "")).toResult(null, null, TokenUsage.ZERO, null);

        assertFalse(result.schemaValid());
    }

    @Test
    @DisplayName("状态读不懂：判不合法，而不是当成失败悄悄放过去")
    void 状态读不懂() {
        ExecResult result = report("SUCCESS", null).toResult(0, null, TokenUsage.ZERO, null);

        assertFalse(result.schemaValid());
        assertTrue(result.summary().contains("SUCCESS"), result.summary());
    }

    @Test
    @DisplayName("状态大小写与空白容忍：执行器写 done 不该被判成读不懂")
    void 状态大小写容忍() {
        ExecResult result = report(" done ", null).toResult(0, null, TokenUsage.ZERO, null);

        assertEquals(RunStatus.SUCCESS, result.status());
        assertNull(result.decision());
    }

    @Test
    @DisplayName("发现缺陷未缺项时原样带过，由结算层落成 Bug 单")
    void 缺陷原样带过() {
        ExecResult result = ExecReport.of("DONE", "做完了", List.of(), null, List.of(
                        new ReportedBug("分页错位", "MINOR", "翻页后重复", "src/page.ts", "offset 计算错误")))
                .toResult(0, "th-1", TokenUsage.ZERO, null);

        assertEquals(1, result.bugs().size());
        assertEquals("分页错位", result.bugs().get(0).title());
    }

    @Test
    @DisplayName("缺陷缺标题或严重级时判不合法，不让半成品进入 Bug 单")
    void 缺陷缺字段不合法() {
        ExecResult result = ExecReport.of("DONE", "做完了", List.of(), null, List.of(
                        new ReportedBug("", "MINOR", "", "", "")))
                .toResult(0, "th-1", TokenUsage.ZERO, null);

        assertFalse(result.schemaValid());
        assertTrue(result.summary().contains("bugs"), result.summary());
    }

    private static ExecReport report(String status, ExecReport.Decision decision) {
        return ExecReport.of(status, "这一轮做了什么", List.of("src/a.ts"), decision, List.of());
    }

    private static ExecReport.Decision decision(String level, boolean reversible) {
        return new ExecReport.Decision(level, "要不要动依赖？", List.of("动", "不动"), "不动", reversible, "构建");
    }
}
