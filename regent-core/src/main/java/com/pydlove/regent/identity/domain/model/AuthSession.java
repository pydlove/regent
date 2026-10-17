package com.pydlove.regent.identity.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

import java.time.Instant;
import java.util.Objects;

/** 一次登录会话。只持有用户 ID，不持有另一个聚合根。 */
public class AuthSession {

    private AuthSessionId id;
    private final UserId userId;
    private final SessionTokenHash tokenHash;
    private final Instant expiresAt;
    private final Instant createdAt;

    private AuthSession(AuthSessionId id,
                        UserId userId,
                        SessionTokenHash tokenHash,
                        Instant expiresAt,
                        Instant createdAt) {
        this.id = id;
        this.userId = Objects.requireNonNull(userId, "用户 ID");
        this.tokenHash = Objects.requireNonNull(tokenHash, "会话令牌哈希");
        this.expiresAt = Objects.requireNonNull(expiresAt, "会话到期时间");
        this.createdAt = Objects.requireNonNull(createdAt, "会话创建时间");
        if (!expiresAt.isAfter(createdAt)) {
            throw new DomainException("会话到期时间必须晚于创建时间");
        }
    }

    public static AuthSession open(UserId userId,
                                   SessionTokenHash tokenHash,
                                   Instant createdAt,
                                   Instant expiresAt) {
        return new AuthSession(null, userId, tokenHash, expiresAt, createdAt);
    }

    public void assignIdentity(AuthSessionId assigned) {
        if (this.id != null) {
            throw new DomainException("会话已有 ID，不能重复分配");
        }
        this.id = Objects.requireNonNull(assigned, "会话 ID");
    }

    public AuthSessionId getId() {
        return id;
    }

    public UserId getUserId() {
        return userId;
    }

    public SessionTokenHash getTokenHash() {
        return tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
