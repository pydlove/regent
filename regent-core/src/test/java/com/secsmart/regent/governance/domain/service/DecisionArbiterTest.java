package com.secsmart.regent.governance.domain.service;

import com.secsmart.regent.governance.domain.model.ArbitrationInput;
import com.secsmart.regent.governance.domain.model.ArbitrationResult;
import com.secsmart.regent.governance.domain.model.DecisionRule;
import com.secsmart.regent.governance.domain.model.RuleType;
import com.secsmart.regent.shared.domain.DomainException;
import com.secsmart.regent.shared.kernel.DecisionLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 决策仲裁器的测试。
 *
 * <p>这是整个平台的关键路径：判错的后果是 AI 自主执行了本该由人拍板的改动。因此这里覆盖的
 * 不只是正常路径，更包括「规则企图降级」「别家项目的规则误命中」「规则写错」这几类会造成
 * 静默失效的场景。
 */
class DecisionArbiterTest {

    private static final Long PROJECT_ID = 7L;
    private static final Long OTHER_PROJECT_ID = 99L;
    private static final DecisionLevel THRESHOLD = DecisionLevel.L1;

    private final DecisionArbiter arbiter = new DecisionArbiter();

    // ---------------------------------------------------------------- 基本行为

    @Test
    @DisplayName("没有任何规则命中时，生效等级等于 AI 自评，且不算被升级")
    void keepsAiLevelWhenNothingMatches() {
        ArbitrationResult result = arbitrate(
                DecisionLevel.L2, null, "", null,
                pathRule(1L, "deploy/**", DecisionLevel.L3));

        assertEquals(DecisionLevel.L2, result.effectiveLevel());
        assertFalse(result.escalated());
        assertNull(result.matchedRuleId());
        assertNull(result.matchedRuleName());
    }

    @Test
    @DisplayName("路径规则命中时把等级抬上去，并记录命中的规则与升级标记")
    void escalatesWhenPathRuleMatches() {
        ArbitrationResult result = arbitrate(
                DecisionLevel.L0, List.of("deploy/prod/upgrade.sh"), "", null,
                pathRule(42L, "deploy/**", DecisionLevel.L3));

        assertEquals(DecisionLevel.L3, result.effectiveLevel());
        assertEquals(DecisionLevel.L0, result.aiLevel());
        assertTrue(result.escalated());
        assertEquals(42L, result.matchedRuleId());
    }

    @Test
    @DisplayName("多条规则同时命中时取最高等级")
    void takesHighestAmongMatchingRules() {
        ArbitrationResult result = arbitrate(
                DecisionLevel.L0, List.of("src/main/resources/db/migration/V1__init.sql"), "", null,
                pathRule(1L, "**/*.sql", DecisionLevel.L2),
                pathRule(2L, "**/migration/**", DecisionLevel.L3));

        assertEquals(DecisionLevel.L3, result.effectiveLevel());
    }

    // ---------------------------------------------------------------- 关键安全约束

    @Test
    @DisplayName("★ 规则只能升级，绝不能把 AI 自评的 L3 压回 L0")
    void rulesCanNeverDowngrade() {
        ArbitrationResult result = arbitrate(
                DecisionLevel.L3, List.of("docs/guide.md"), "", null,
                pathRule(1L, "docs/**", DecisionLevel.L0),
                pathRule(2L, "**/*.md", DecisionLevel.L0));

        assertEquals(DecisionLevel.L3, result.effectiveLevel());
        assertFalse(result.escalated());
    }

    @Test
    @DisplayName("★ 通配表达式非法时抛异常而不是静默跳过 —— 失败关闭")
    void invalidGlobFailsClosed() {
        DomainException thrown = assertThrows(DomainException.class, () -> arbitrate(
                DecisionLevel.L0, List.of("src/App.java"), "", null,
                pathRule(1L, "src/[unclosed", DecisionLevel.L3)));

        assertTrue(thrown.getMessage().contains("通配表达式非法"));
    }

    // ---------------------------------------------------------------- 规则适用范围

    @Test
    @DisplayName("别家项目的规则不参与匹配 —— 否则一个项目的策略会污染另一个项目")
    void rulesOfOtherProjectsDoNotApply() {
        ArbitrationResult result = arbitrate(
                DecisionLevel.L0, List.of("deploy/prod.sh"), "", null,
                new DecisionRule(1L, OTHER_PROJECT_ID, "别家项目的规则",
                        RuleType.PATH_GLOB, "deploy/**", DecisionLevel.L3, 0, true));

        assertEquals(DecisionLevel.L0, result.effectiveLevel());
        assertNull(result.matchedRuleId());
    }

    @Test
    @DisplayName("全局规则（projectId 为空）对所有项目生效")
    void globalRulesApplyToEveryProject() {
        ArbitrationResult result = arbitrate(
                DecisionLevel.L0, List.of("deploy/prod.sh"), "", null,
                new DecisionRule(1L, null, "全局规则",
                        RuleType.PATH_GLOB, "deploy/**", DecisionLevel.L3, 0, true));

        assertEquals(DecisionLevel.L3, result.effectiveLevel());
    }

    @Test
    @DisplayName("停用的规则不参与匹配")
    void disabledRulesAreSkipped() {
        ArbitrationResult result = arbitrate(
                DecisionLevel.L0, List.of("deploy/prod.sh"), "", null,
                new DecisionRule(1L, null, "已停用", RuleType.PATH_GLOB,
                        "deploy/**", DecisionLevel.L3, 0, false));

        assertEquals(DecisionLevel.L0, result.effectiveLevel());
    }

    @Test
    @DisplayName("同级规则按 id 升序，命中的最高优先级规则被记录下来")
    void higherPriorityRuleIsRecordedAsMatched() {
        ArbitrationResult result = arbitrate(
                DecisionLevel.L0, List.of("deploy/prod.sh"), "", null,
                new DecisionRule(1L, null, "低优先级", RuleType.PATH_GLOB,
                        "deploy/**", DecisionLevel.L2, 5, true),
                new DecisionRule(2L, null, "高优先级", RuleType.PATH_GLOB,
                        "deploy/**", DecisionLevel.L3, 10, true));

        assertEquals(2L, result.matchedRuleId(), "应记录优先级最高的那条");
        assertEquals(DecisionLevel.L3, result.effectiveLevel());
    }

    // ---------------------------------------------------------------- 阈值与人工介入判定

    @Nested
    @DisplayName("自动阈值判定")
    class Threshold {

        @Test
        @DisplayName("生效等级等于阈值时可以自动决策")
        void equalToThresholdIsAutoResolvable() {
            ArbitrationResult result = arbitrate(DecisionLevel.L1, null, "", null);

            assertEquals(THRESHOLD, result.effectiveLevel());
            assertTrue(result.autoResolvable());
            assertFalse(result.requiresHumanApproval());
        }

        @Test
        @DisplayName("生效等级高于阈值时必须人工批准")
        void aboveThresholdRequiresHuman() {
            ArbitrationResult result = arbitrate(DecisionLevel.L2, null, "", null);

            assertTrue(result.requiresHumanApproval());
            assertFalse(result.autoResolvable());
        }

        @Test
        @DisplayName("规则把等级抬过阈值后，原本可自动的决策转为需人工")
        void escalationFlipsAutoToHuman() {
            ArbitrationResult result = arbitrate(
                    DecisionLevel.L1, List.of("deploy/prod.sh"), "", null,
                    pathRule(1L, "deploy/**", DecisionLevel.L2));

            assertTrue(result.requiresHumanApproval());
        }
    }

    // ---------------------------------------------------------------- 各类匹配方式

    @Nested
    @DisplayName("匹配方式")
    class Matching {

        @Test
        @DisplayName("关键词匹配忽略大小写，且匹配决策文本")
        void keywordIgnoresCase() {
            ArbitrationResult result = arbitrate(
                    DecisionLevel.L0, null, "需要 DROP TABLE 还是软删除？", null,
                    new DecisionRule(1L, null, "危险操作关键词", RuleType.KEYWORD,
                            "drop table", DecisionLevel.L3, 0, true));

            assertEquals(DecisionLevel.L3, result.effectiveLevel());
        }

        @Test
        @DisplayName("关键词未出现时不命中")
        void keywordMissingDoesNotMatch() {
            ArbitrationResult result = arbitrate(
                    DecisionLevel.L0, null, "把按钮文案改成保存", null,
                    new DecisionRule(1L, null, "危险操作关键词", RuleType.KEYWORD,
                            "drop table", DecisionLevel.L3, 0, true));

            assertEquals(DecisionLevel.L0, result.effectiveLevel());
        }

        @Test
        @DisplayName("★ 双星号开头的规则也能匹配根目录下的文件，向 Ant 习惯靠齐")
        void doubleStarPrefixAlsoMatchesRootLevelFiles() {
            ArbitrationResult result = arbitrate(
                    DecisionLevel.L0, List.of("README.md"), "", null,
                    pathRule(1L, "**/README.md", DecisionLevel.L2));

            assertEquals(DecisionLevel.L2, result.effectiveLevel(),
                    "Java glob 本身不匹配，必须靠补一次去前缀匹配来兜底");
        }

        @Test
        @DisplayName("双星号规则同时也能匹配深层路径")
        void doubleStarPrefixMatchesNestedFiles() {
            ArbitrationResult result = arbitrate(
                    DecisionLevel.L0, List.of("docs/deep/README.md"), "", null,
                    pathRule(1L, "**/README.md", DecisionLevel.L2));

            assertEquals(DecisionLevel.L2, result.effectiveLevel());
        }

        @Test
        @DisplayName("单星号不跨越目录分隔符")
        void singleStarDoesNotCrossSeparators() {
            ArbitrationResult result = arbitrate(
                DecisionLevel.L0, List.of("a/b/c.sql"), "", null,
                pathRule(1L, "*.sql", DecisionLevel.L2));

            assertEquals(DecisionLevel.L0, result.effectiveLevel());
        }
    }

    @Nested
    @DisplayName("可逆性规则")
    class Reversibility {

        @Test
        @DisplayName("改动不可逆且规则要求 false 时命中")
        void matchesIrreversible() {
            ArbitrationResult result = arbitrate(
                    DecisionLevel.L0, null, "", Boolean.FALSE,
                    reversibilityRule(1L, "false", DecisionLevel.L3));

            assertEquals(DecisionLevel.L3, result.effectiveLevel());
        }

        @Test
        @DisplayName("改动可逆时逆向规则不命中")
        void doesNotMatchReversible() {
            ArbitrationResult result = arbitrate(
                    DecisionLevel.L0, null, "", Boolean.TRUE,
                    reversibilityRule(1L, "false", DecisionLevel.L3));

            assertEquals(DecisionLevel.L0, result.effectiveLevel());
        }

        @Test
        @DisplayName("可逆性未知时不命中 —— 拿不准就不要据此提升等级")
        void unknownReversibilityDoesNotMatch() {
            ArbitrationResult result = arbitrate(
                    DecisionLevel.L0, null, "", null,
                    reversibilityRule(1L, "false", DecisionLevel.L3));

            assertEquals(DecisionLevel.L0, result.effectiveLevel());
        }

        @Test
        @DisplayName("可逆性 pattern 不是布尔值时抛异常")
        void invalidPatternThrows() {
            assertThrows(DomainException.class, () -> arbitrate(
                    DecisionLevel.L0, null, "", Boolean.TRUE,
                    reversibilityRule(1L, "maybe", DecisionLevel.L3)));
        }
    }

    // ---------------------------------------------------------------- 测试辅助

    private ArbitrationResult arbitrate(DecisionLevel aiLevel,
                                       List<String> changedPaths,
                                       String decisionText,
                                       Boolean reversible,
                                       DecisionRule... rules) {
        return arbiter.arbitrate(new ArbitrationInput(
                PROJECT_ID, aiLevel, THRESHOLD, changedPaths, decisionText, reversible, List.of(rules)));
    }

    private static DecisionRule pathRule(Long id, String pattern, DecisionLevel target) {
        return new DecisionRule(id, null, "路径规则 " + pattern, RuleType.PATH_GLOB,
                pattern, target, 0, true);
    }

    private static DecisionRule reversibilityRule(Long id, String pattern, DecisionLevel target) {
        return new DecisionRule(id, null, "可逆性规则 " + pattern, RuleType.REVERSIBILITY,
                pattern, target, 0, true);
    }
}
