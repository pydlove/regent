package com.pydlove.regent.governance.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

/**
 * 一次性审批令牌 —— 邮件里那条「点一下就批准」的链接所带的东西。
 *
 * <p>它存在的前提是：邮件会离开服务器、进到别人的收件箱和邮箱服务商。因此链接里不能带
 * 「决策号 + 谁都能猜到的参数」，只能带一枚随机令牌；而且令牌本身也**不落明文** ——
 * 库里存的是 SHA-256，泄库也换不来一次批准。
 *
 * <p>一次性由 {@code status} 与 {@code expires_at} 共同保证：核销后置为 USED，
 * 并且带过期时间。两者缺一不可 —— 只有过期时间的话，邮件被转发一次就多一个人能批。
 */
public record ApprovalToken(long id, DecisionId decisionId, String tokenHash, String status, Instant expiresAt) {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_USED = "USED";

    public ApprovalToken {
        if (id <= 0) {
            throw new DomainException("令牌 ID 必须为正数：" + id);
        }
        if (decisionId == null) {
            throw new DomainException("令牌必须挂在某条决策上");
        }
        if (tokenHash == null || tokenHash.isBlank()) {
            throw new DomainException("令牌哈希不能为空");
        }
    }

    /** 明文令牌 → 入库用的 SHA-256 十六进制串。 */
    public static String hashOf(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new DomainException("令牌不能为空");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JDK 必须提供的算法，走不到这里；真走到了说明运行环境坏了，不该静默继续
            throw new IllegalStateException("运行环境缺少 SHA-256", e);
        }
    }

    /**
     * 明文令牌是否与本条记录匹配。
     *
     * <p>用 {@link MessageDigest#isEqual} 而不是 {@code equals}：后者逐字节短路比较，
     * 比较耗时会随匹配前缀变化，理论上可被用来一位一位试出哈希。
     */
    public boolean matches(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                tokenHash.getBytes(StandardCharsets.UTF_8),
                hashOf(rawToken).getBytes(StandardCharsets.UTF_8));
    }

    /** 现在还能不能用。已核销、已过期、没有过期时间的，一律视为不可用（失败关闭）。 */
    public boolean isUsable(Instant now) {
        return STATUS_ACTIVE.equals(status) && expiresAt != null && expiresAt.isAfter(now);
    }

    public ApprovalToken consumed() {
        return new ApprovalToken(id, decisionId, tokenHash, STATUS_USED, expiresAt);
    }
}
