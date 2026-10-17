package com.pydlove.regent.governance.application.command;

import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.governance.domain.model.Decision;
import com.pydlove.regent.governance.domain.model.DecisionRule;
import com.pydlove.regent.governance.domain.model.DecisionStatus;
import com.pydlove.regent.governance.domain.model.ResolvedBy;
import com.pydlove.regent.governance.domain.model.RuleType;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.kernel.DecisionLevel;
import com.pydlove.regent.support.ExecutionFakes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 建单用例测试 —— 核心域的入口。
 *
 * <p>这里守的是「AI 到底能不能自己拍板」这一个判断。每一个分支都对应一种真实的自信心边界：
 * 阈值内、阈值外、可逆、不可逆、规则命中、规则静默。
 */
class DecisionRaiseServiceTest {

    private static final ProjectId PROJECT = ProjectId.of(1L);
    private static final TaskId TASK = TaskId.of(9L);

    private final ExecutionFakes.Rules rules = new ExecutionFakes.Rules();
    private final ExecutionFakes.Decisions decisions = new ExecutionFakes.Decisions();
    private final DecisionRaiseService service = new DecisionRaiseService(rules, decisions);

    private RaiseDecisionCommand command(DecisionLevel aiLevel,
                                         DecisionLevel threshold,
                                         boolean reversible,
                                         String recommendation,
                                         List<String> options) {
        return command(aiLevel, threshold, reversible, recommendation, options, List.of());
    }

    private RaiseDecisionCommand command(DecisionLevel aiLevel,
                                         DecisionLevel threshold,
                                         boolean reversible,
                                         String recommendation,
                                         List<String> options,
                                         List<String> changedPaths) {
        return new RaiseDecisionCommand(TASK, PROJECT, threshold, aiLevel,
                "要不要动构建脚本？", options, recommendation, reversible, "构建流程", changedPaths);
    }

    private DecisionRule rule(long id, RuleType type, String pattern, DecisionLevel level) {
        return new DecisionRule(id, null, "规则" + id, type, pattern, level, 100, true);
    }

    @Nested
    @DisplayName("自动决断")
    class AutoResolve {

        @Test
        void 阈值内且可逆时AI自主决断并留痕() {
            RaisedDecision raised = service.raise(command(DecisionLevel.L1, DecisionLevel.L1, true,
                    "按现有约定补测试", List.of("补测试")));

            Decision decision = raised.decision();
            assertFalse(raised.needsHuman());
            assertEquals(DecisionStatus.AUTO_RESOLVED, decision.getStatus());
            assertEquals(ResolvedBy.AI, decision.getResolvedBy().orElseThrow());
            assertEquals("按现有约定补测试", decision.getResolvedChoice().orElseThrow());
            assertTrue(decision.getResolutionNote().orElseThrow().contains("AI 自评 L1"),
                    "留痕里必须能看出定级依据");
            assertEquals(1, decisions.saves, "自主决断的决策同样要落库，否则等于没留痕");
        }

        @Test
        void 没有建议时取第一个候选方案() {
            RaisedDecision raised = service.raise(command(DecisionLevel.L0, DecisionLevel.L1, true,
                    null, List.of("加索引", "改查询")));

            assertEquals("加索引", raised.decision().getResolvedChoice().orElseThrow());
        }

        @Test
        void 建议超长时截断而不是让整条决策失败() {
            String longRecommendation = "很长的建议".repeat(30);

            RaisedDecision raised = service.raise(command(DecisionLevel.L0, DecisionLevel.L1, true,
                    longRecommendation, List.of()));

            assertEquals(64, raised.decision().getResolvedChoice().orElseThrow().length());
        }

        @Test
        void 规则抬到阈值内仍然可以自主决断() {
            // 自评 L0，规则把它抬到 L1 —— 抬了，但仍在阈值 L1 之内，所以还是 AI 自己拍
            rules.with(rule(1, RuleType.KEYWORD, "构建脚本", DecisionLevel.L1));

            RaisedDecision raised = service.raise(command(DecisionLevel.L0, DecisionLevel.L1, true,
                    null, List.of()));

            assertFalse(raised.needsHuman());
            assertTrue(raised.arbitration().escalated());
            assertEquals(DecisionLevel.L1, raised.decision().getEffectiveLevel());
        }
    }

    @Nested
    @DisplayName("必须人工")
    class NeedsHuman {

        @Test
        void 生效等级超过阈值时留给人工() {
            RaisedDecision raised = service.raise(command(DecisionLevel.L2, DecisionLevel.L1, true,
                    "建议不动", List.of("动", "不动")));

            assertTrue(raised.needsHuman());
            assertEquals(DecisionStatus.PENDING, raised.decision().getStatus());
            assertTrue(raised.decision().getResolvedChoice().isEmpty());
        }

        @Test
        void 不可逆时即使阈值内也留给人工() {
            RaisedDecision raised = service.raise(command(DecisionLevel.L0, DecisionLevel.L3, false,
                    "建议执行迁移", List.of("执行")));

            assertTrue(raised.needsHuman(), "不可逆的改动不许 AI 自主决断，这条不依赖任何阈值配置");
            assertEquals(DecisionStatus.PENDING, raised.decision().getStatus());
        }

        @Test
        void 强制人工的动作即使阈值内且可逆也必须等待批准() {
            RaiseDecisionCommand branchApproval = new RaiseDecisionCommand(
                    TASK, PROJECT, DecisionLevel.L3, DecisionLevel.L2,
                    "允许创建任务分支吗？", List.of("允许", "不允许"), "允许", true,
                    "只影响本地分支", List.of(), true);

            RaisedDecision raised = service.raise(branchApproval);

            assertTrue(raised.needsHuman(), "分支创建属于平台安全动作，不能被项目阈值自动放行");
            assertEquals(DecisionStatus.PENDING, raised.decision().getStatus());
        }

        @Test
        void 规则命中路径通配把等级抬过阈值() {
            rules.with(rule(7, RuleType.PATH_GLOB, "deploy/**", DecisionLevel.L3));

            RaisedDecision raised = service.raise(command(DecisionLevel.L0, DecisionLevel.L1, true,
                    null, List.of(), List.of("deploy/local/start-all.sh")));

            assertTrue(raised.needsHuman());
            assertEquals(DecisionLevel.L3, raised.decision().getEffectiveLevel());
            assertEquals(7L, raised.decision().getMatchedRuleId().orElseThrow());
        }

        @Test
        void 关键词命中问题文本时也升级() {
            rules.with(rule(3, RuleType.KEYWORD, "DROP TABLE", DecisionLevel.L3));

            RaisedDecision raised = service.raise(new RaiseDecisionCommand(TASK, PROJECT,
                    DecisionLevel.L1, DecisionLevel.L0, "要不要 DROP TABLE 重建？",
                    List.of(), null, true, null, List.of()));

            assertEquals(DecisionLevel.L3, raised.decision().getEffectiveLevel());
            assertTrue(raised.needsHuman());
        }
    }

    @Test
    void 停用的规则不参与仲裁() {
        rules.with(new DecisionRule(4L, null, "停用规则", RuleType.KEYWORD, "构建脚本",
                DecisionLevel.L3, 100, false));

        RaisedDecision raised = service.raise(command(DecisionLevel.L0, DecisionLevel.L1, true, null, List.of()));

        assertEquals(DecisionLevel.L0, raised.decision().getEffectiveLevel());
        assertFalse(raised.needsHuman());
    }
}
