package com.pydlove.regent.governance.domain.model;

import com.pydlove.regent.shared.domain.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 审批令牌测试。
 *
 * <p>重点在「什么时候不能用」：令牌是这套系统里唯一一条不需要登录就能改变状态的入口
 * （邮件链接），它的边界条件漏一个，就等于多一条免登录的写路径。
 */
class ApprovalTokenTest {

    private static final Instant NOW = Instant.parse("2026-10-16T06:00:00Z");

    private ApprovalToken activeToken(String rawToken) {
        return new ApprovalToken(1L, DecisionId.of(7L), ApprovalToken.hashOf(rawToken), "ACTIVE",
                NOW.plusSeconds(3600));
    }

    @Nested
    @DisplayName("哈希")
    class Hashing {

        @Test
        void 同一明文哈希稳定且不落明文() {
            String hash = ApprovalToken.hashOf("demo-token-1");

            assertTrue(hash.length() == 64, "SHA-256 的十六进制串应为 64 位：" + hash.length());
            assertNotEquals("demo-token-1", hash);
            assertTrue(hash.equals(ApprovalToken.hashOf("demo-token-1")));
            assertFalse(hash.equals(ApprovalToken.hashOf("demo-token-2")));
        }

        @Test
        void 空令牌拒绝哈希() {
            assertThrows(DomainException.class, () -> ApprovalToken.hashOf(" "));
            assertThrows(DomainException.class, () -> ApprovalToken.hashOf(null));
        }
    }

    @Nested
    @DisplayName("比对")
    class Matching {

        @Test
        void 只有明文一致才算匹配() {
            ApprovalToken token = activeToken("correct-horse");

            assertTrue(token.matches("correct-horse"));
            assertFalse(token.matches("battery-staple"));
            assertFalse(token.matches(""));
            assertFalse(token.matches(null));
        }
    }

    @Nested
    @DisplayName("可用性")
    class Usability {

        @Test
        void 未过期且未核销时可用() {
            assertTrue(activeToken("t").isUsable(NOW));
        }

        @Test
        void 已核销的不可用() {
            ApprovalToken used = activeToken("t").consumed();

            assertFalse(used.isUsable(NOW));
            assertTrue(used.matches("t"), "核销只改状态，不该动哈希");
        }

        @Test
        void 已过期的不可用() {
            assertFalse(activeToken("t").isUsable(NOW.plusSeconds(3601)));
        }

        @Test
        void 恰好到点即失效() {
            // 用 isAfter 而不是 isAfterOrEqual：过期时刻那一瞬间就算失效，
            // 否则「过期时间」到底含不含端点会随实现漂移，而这种边界最难在产品上被发现
            assertFalse(activeToken("t").isUsable(NOW.plusSeconds(3600)));
        }

        @Test
        void 没有过期时间的不可用() {
            ApprovalToken noExpiry = new ApprovalToken(1L, DecisionId.of(7L), ApprovalToken.hashOf("t"),
                    "ACTIVE", null);

            assertFalse(noExpiry.isUsable(NOW));
        }
    }
}
