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
    private DisplayName displayName;
    private UserAvatar avatar;
    private final Instant createdAt;

    private User(UserId id,
                 Email email,
                 Username username,
                 DisplayName displayName,
                 PasswordHash passwordHash,
                 UserAvatar avatar,
                 Instant createdAt) {
        this.id = id;
        this.email = email;
        this.username = Objects.requireNonNull(username, "用户名");
        this.displayName = Objects.requireNonNull(displayName, "显示名称");
        this.passwordHash = Objects.requireNonNull(passwordHash, "密码哈希");
        this.avatar = avatar;
        if (createdAt == null) {
            throw new DomainException("用户创建时间不能为空");
        }
        this.createdAt = createdAt;
    }

    public static User register(Email email,
                                Username username,
                                PasswordHash passwordHash,
                                Instant now) {
        Username checkedUsername = Objects.requireNonNull(username, "用户名");
        return new User(
                null,
                Objects.requireNonNull(email, "注册邮箱"),
                checkedUsername,
                DisplayName.of(checkedUsername.value()),
                passwordHash,
                null,
                now);
    }

    public static User restore(UserId id,
                               Email email,
                               Username username,
                               DisplayName displayName,
                               PasswordHash passwordHash,
                               UserAvatar avatar,
                               Instant createdAt) {
        if (id == null) {
            throw new DomainException("已持久化用户必须有 ID");
        }
        return new User(id, email, username, displayName, passwordHash, avatar, createdAt);
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

    public DisplayName getDisplayName() {
        return displayName;
    }

    public PasswordHash getPasswordHash() {
        return passwordHash;
    }

    public UserAvatar getAvatar() {
        return avatar;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void updateDisplayName(DisplayName displayName) {
        this.displayName = Objects.requireNonNull(displayName, "显示名称");
    }

    public void changeAvatar(UserAvatar avatar) {
        this.avatar = Objects.requireNonNull(avatar, "头像");
    }
}
