package com.pydlove.regent.identity.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

import java.time.Instant;
import java.util.Objects;

/** 平台用户。密码只保存编码后的哈希，聚合本身不提供修改凭据的入口。 */
public class User {

    private UserId id;
    private final Email email;
    private final Username username;
    private final PasswordHash passwordHash;
    private final Instant createdAt;

    private User(UserId id,
                 Email email,
                 Username username,
                 PasswordHash passwordHash,
                 Instant createdAt) {
        this.id = id;
        this.email = email;
        this.username = Objects.requireNonNull(username, "用户名");
        this.passwordHash = Objects.requireNonNull(passwordHash, "密码哈希");
        if (createdAt == null) {
            throw new DomainException("用户创建时间不能为空");
        }
        this.createdAt = createdAt;
    }

    public static User register(Email email,
                                Username username,
                                PasswordHash passwordHash,
                                Instant now) {
        return new User(null, Objects.requireNonNull(email, "注册邮箱"), username, passwordHash, now);
    }

    public static User restore(UserId id,
                               Email email,
                               Username username,
                               PasswordHash passwordHash,
                               Instant createdAt) {
        if (id == null) {
            throw new DomainException("已持久化用户必须有 ID");
        }
        return new User(id, email, username, passwordHash, createdAt);
    }

    public void assignIdentity(UserId assigned) {
        if (this.id != null) {
            throw new DomainException("用户已有 ID，不能重复分配：" + this.id.value());
        }
        this.id = Objects.requireNonNull(assigned, "用户 ID");
    }

    public UserId getId() {
        return id;
    }

    public Email getEmail() {
        return email;
    }

    public Username getUsername() {
        return username;
    }

    public PasswordHash getPasswordHash() {
        return passwordHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
