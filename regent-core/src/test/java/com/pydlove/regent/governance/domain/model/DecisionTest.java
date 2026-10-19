package com.pydlove.regent.governance.domain.model;

import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.kernel.DecisionLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 决策单聚合测试。
 *
 * <p>三条不变量各有一组用例：只有待批能决议、生效等级不得低于自评、不可逆不许 AI 自主决断。
 * 它们不是「防御性编程」，而是审计链本身 —— 任何一条被绕过，事后都无法回答
 * 「这条改动是谁放行的」。
 */
class DecisionTest {

    private static final TaskId TASK = TaskId.of(42L);
    private static final ProjectId PROJECT = ProjectId.of(1L);

    private Decision raise(DecisionLevel ai, DecisionLevel effective, boolean reversible) {
        return Decision.raise(new DecisionRaise(
                TASK, PROJECT, ai, effective, 3L, "要不要给订单表加 refund_no 字段？",
                List.of("加字段并补索引", "先加字段"), "建议加字段并补索引，走 INSTANT 算法",
                reversible, "orders 表（生产约 120 万行）"));
    }

    private Decision pending() {
        return raise(DecisionLevel.L1, DecisionLevel.L2, true);
    }

    @Nested
    @DisplayName("提出")
    class Raising {

        @Test
        void 新决策从待批开始并保留自评与生效两个等级() {
            Decision decision = pending();

            assertEquals(DecisionStatus.PENDING, decision.getStatus());
            assertEquals(DecisionLevel.L1, decision.getAiLevel());
            assertEquals(DecisionLevel.L2, decision.getEffectiveLevel());
            assertEquals(3L, decision.getMatchedRuleId().orElseThrow());
            assertTrue(decision.isPending());
            assertTrue(decision.getResolvedBy().isEmpty());
            assertTrue(decision.getResolvedAt().isEmpty());
            assertNotNull(decision.getRaisedAt());
        }

        @Test
        void 问题为空拒绝建单() {
            assertThrows(DomainException.class, () -> Decision.raise(new DecisionRaise(
                    TASK, PROJECT, DecisionLevel.L0, DecisionLevel.L0, null, "  ",
                    List.of(), null, true, null)));
        }

        @Test
        void 生效等级低于自评等级拒绝建单() {
            // 仲裁只升不降。建单时若能传进一个更低的生效等级，那条规则就形同虚设
            DomainException e = assertThrows(DomainException.class,
                    () -> raise(DecisionLevel.L3, DecisionLevel.L1, false));

            assertTrue(e.getMessage().contains("不得低于"), e.getMessage());
        }
    }

    @Nested
    @DisplayName("人工决议")
    class HumanReview {

        @Test
        void 批准后落人工决议人并留下选项() {
            Decision decision = pending();

            decision.approve("加字段并补索引", "看过表结构，可以执行");

            assertEquals(DecisionStatus.APPROVED, decision.getStatus());
            assertEquals(ResolvedBy.HUMAN, decision.getResolvedBy().orElseThrow());
            assertEquals("加字段并补索引", decision.getResolvedChoice().orElseThrow());
            assertEquals("看过表结构，可以执行", decision.getResolutionNote().orElseThrow());
            assertNotNull(decision.getResolvedAt());
            assertFalse(decision.isPending());
        }

        @Test
        void 批准时选项与说明都可以为空() {
            Decision decision = pending();

            decision.approve(null, null);

            assertEquals(DecisionStatus.APPROVED, decision.getStatus());
            assertTrue(decision.getResolvedChoice().isEmpty());
            assertTrue(decision.getResolutionNote().isEmpty());
        }

        @Test
        void 驳回时理由可以留空() {
            Decision decision = pending();

            decision.reject(null, "   ");

            assertEquals(DecisionStatus.REJECTED, decision.getStatus());
            assertTrue(decision.getResolutionNote().isEmpty());
        }

        @Test
        void 驳回后状态为已驳回且理由入库() {
            Decision decision = pending();

            decision.reject(null, "这个改动会影响线上数据，先人工确认");

            assertEquals(DecisionStatus.REJECTED, decision.getStatus());
            assertEquals(ResolvedBy.HUMAN, decision.getResolvedBy().orElseThrow());
        }

        @Test
        void 选项超长被拒() {
            Decision decision = pending();

            assertThrows(DomainException.class, () -> decision.approve("选".repeat(65), null));
        }
    }

    @Nested
    @DisplayName("AI 自主决断")
    class AutoResolution {

        @Test
        void 阈值内且可逆时由AI决断并留痕() {
            Decision decision = raise(DecisionLevel.L0, DecisionLevel.L1, true);

            decision.autoResolve(DecisionLevel.L1, "按类型拆方法", "改动只在 execution 内部");

            assertEquals(DecisionStatus.AUTO_RESOLVED, decision.getStatus());
            assertEquals(ResolvedBy.AI, decision.getResolvedBy().orElseThrow());
        }

        @Test
        void 不可逆的改动一律不许AI自主决断() {
            // 这条不依赖规则配置：哪怕阈值配到 L3、一条规则都没命中，只要执行器说改动不可逆，
            // 就必须由人点头
            Decision decision = raise(DecisionLevel.L0, DecisionLevel.L0, false);

            DomainException e = assertThrows(DomainException.class,
                    () -> decision.autoResolve(DecisionLevel.L3, "删掉旧表", null));

            assertTrue(e.getMessage().contains("不可逆"), e.getMessage());
            assertEquals(DecisionStatus.PENDING, decision.getStatus());
        }

        @Test
        void 生效等级超出阈值时必须人工() {
            Decision decision = raise(DecisionLevel.L1, DecisionLevel.L3, true);

            DomainException e = assertThrows(DomainException.class,
                    () -> decision.autoResolve(DecisionLevel.L1, "继续", null));

            assertTrue(e.getMessage().contains("阈值"), e.getMessage());
            assertEquals(DecisionStatus.PENDING, decision.getStatus());
        }
    }

    @Nested
    @DisplayName("重复决议")
    class DoubleReview {

        @Test
        void 已批准的不能再次批准或驳回() {
            Decision decision = pending();
            decision.approve("加字段并补索引", null);

            assertThrows(DomainException.class, () -> decision.approve("再来一次", null));
            assertThrows(DomainException.class, () -> decision.reject(null, "反悔了，理由够长"));
            assertThrows(DomainException.class,
                    () -> decision.autoResolve(DecisionLevel.L3, "AI 自己放行", null));
        }

        @Test
        void 失败的那次决议不该覆盖首次决议的留痕() {
            Decision decision = pending();
            decision.approve("加字段并补索引", "第一次的判断");

            assertThrows(DomainException.class, () -> decision.reject("先加字段", "反悔理由足够长"));

            assertEquals(ResolvedBy.HUMAN, decision.getResolvedBy().orElseThrow());
            assertEquals("加字段并补索引", decision.getResolvedChoice().orElseThrow());
            assertEquals("第一次的判断", decision.getResolutionNote().orElseThrow());
        }
    }

    @Nested
    @DisplayName("重建")
    class Restoring {

        @Test
        void 从快照还原后状态与留痕一致() {
            Decision original = pending();
            original.assignIdentity(DecisionId.of(5L));
            original.reject(null, "这个改动会影响线上数据");

            Decision restored = Decision.restore(new DecisionSnapshot(
                    original.getId(), original.getTaskId(), original.getProjectId(),
                    original.getAiLevel(), original.getEffectiveLevel(),
                    original.getMatchedRuleId().orElse(null), original.getStatus(),
                    original.getQuestion(), original.getOptions(),
                    original.getRecommendation().orElse(null), original.isReversible(),
                    original.getBlastRadius().orElse(null),
                    original.getResolvedBy().orElse(null),
                    original.getResolvedChoice().orElse(null),
                    original.getResolutionNote().orElse(null),
                    original.getRaisedAt(), original.getResolvedAt().orElse(null),
                    original.getCreatedAt(), original.getUpdatedAt()));

            assertEquals(original.getStatus(), restored.getStatus());
            assertEquals(original.getResolvedBy(), restored.getResolvedBy());
            assertEquals(original.getResolvedAt(), restored.getResolvedAt());
            assertFalse(restored.isPending());
        }

        @Test
        void 主键只能分配一次() {
            Decision decision = pending();

            decision.assignIdentity(DecisionId.of(5L));

            assertThrows(DomainException.class, () -> decision.assignIdentity(DecisionId.of(6L)));
        }
    }
}
