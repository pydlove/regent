package com.pydlove.regent.identity.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** 一次邮箱验证码挑战。验证码只能成功消费一次，连续输错达到上限后立即失效。 */
public class EmailVerificationCode {

    private static final int MAX_FAILED_ATTEMPTS = 5;

    private Long id;
    private final Email email;
    private final EmailVerificationCodeHash codeHash;
    private final Instant createdAt;
    private final Instant expiresAt;
    private Instant consumedAt;
    private int failedAttempts;

    private EmailVerificationCode(Long id,
                                  Email email,
                                  EmailVerificationCodeHash codeHash,
                                  Instant createdAt,
                                  Instant expiresAt,
                                  Instant consumedAt,
                                  int failedAttempts) {
        this.id = id;
        this.email = Objects.requireNonNull(email, "邮箱");
        this.codeHash = Objects.requireNonNull(codeHash, "验证码摘要");
        this.createdAt = Objects.requireNonNull(createdAt, "创建时间");
        this.expiresAt = Objects.requireNonNull(expiresAt, "失效时间");
        this.consumedAt = consumedAt;
        this.failedAttempts = failedAttempts;
    }

    public static EmailVerificationCode issue(Email email,
                                              EmailVerificationCodeHash codeHash,
                                              Instant now,
                                              Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new DomainException("验证码有效期必须大于零");
        }
        return new EmailVerificationCode(null, email, codeHash, now, now.plus(ttl), null, 0);
    }

    public static EmailVerificationCode restore(Long id,
                                                Email email,
                                                EmailVerificationCodeHash codeHash,
                                                Instant createdAt,
                                                Instant expiresAt,
                                                Instant consumedAt,
                                                int failedAttempts) {
        if (id == null) {
            throw new DomainException("已持久化验证码必须有 ID");
        }
        return new EmailVerificationCode(
                id, email, codeHash, createdAt, expiresAt, consumedAt, failedAttempts);
    }

    public boolean verify(EmailVerificationCodeHash candidate, Instant now) {
        if (!isActive(now)) {
            return false;
        }
        if (!codeHash.equals(candidate)) {
            failedAttempts++;
            if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
                consumedAt = now;
            }
            return false;
        }
        consumedAt = now;
        return true;
    }

    public void invalidate(Instant now) {
        if (consumedAt == null) {
            consumedAt = Objects.requireNonNull(now, "失效时间");
        }
    }

    public boolean isActive(Instant now) {
        return consumedAt == null && expiresAt.isAfter(now);
    }

    public void assignIdentity(Long assigned) {
        if (this.id != null) {
            throw new DomainException("验证码已有 ID，不能重复分配：" + this.id);
        }
        this.id = Objects.requireNonNull(assigned, "验证码 ID");
    }

    public Long getId() {
        return id;
    }

    public Email getEmail() {
        return email;
    }

    public EmailVerificationCodeHash getCodeHash() {
        return codeHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }
}
