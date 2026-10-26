package com.pydlove.regent.identity.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

import java.time.Instant;
import java.util.Objects;

/** 平台用户。密码只保存编码后的哈希，修改密码必须经过领域方法。 */
public class User {

    private UserId id;
    private final Email email;
    private final Username username;
    private PasswordHash passwordHash;
    private boolean mustChangePassword;
    private DisplayName displayName;
    private UserAvatar avatar;
    private final Instant createdAt;

    private User(UserId id,
                 Email email,
                 Username username,
                 DisplayName displayName,
                 PasswordHash passwordHash,
                 boolean mustChangePassword,
                 UserAvatar avatar,
                 Instant createdAt) {
        this.id = id;
        this.email = email;
        this.username = Objects.requireNonNull(username, "用户名");
        this.displayName = Objects.requireNonNull(displayName, "显示名称");
        this.passwordHash = Objects.requireNonNull(passwordHash, "密码哈希");
        this.mustChangePassword = mustChangePassword;
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
                false,
                null,
                now);
    }

    /** 管理员手动创建账号时使用：初始密码只用于首次登录。 */
    public static User registerTemporary(Email email,
                                         Username username,
                                         DisplayName displayName,
                                         PasswordHash passwordHash,
                                         Instant now) {
        return new User(
                null,
                Objects.requireNonNull(email, "账号邮箱"),
                Objects.requireNonNull(username, "用户名"),
                Objects.requireNonNull(displayName, "显示名称"),
                passwordHash,
                true,
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
        return restore(id, email, username, displayName, passwordHash, false, avatar, createdAt);
    }

    public static User restore(UserId id,
                               Email email,
                               Username username,
                               DisplayName displayName,
                               PasswordHash passwordHash,
                               boolean mustChangePassword,
                               UserAvatar avatar,
                               Instant createdAt) {
        if (id == null) {
            throw new DomainException("已持久化用户必须有 ID");
        }
        return new User(id, email, username, displayName, passwordHash,
                mustChangePassword, avatar, createdAt);
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

    public boolean mustChangePassword() {
        return mustChangePassword;
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

    public void changePassword(PasswordHash nextPasswordHash) {
        this.passwordHash = Objects.requireNonNull(nextPasswordHash, "新密码哈希");
        this.mustChangePassword = false;
    }

    /** 管理员重置密码后必须重新进入首次登录改密流程。 */
    public void resetTemporaryPassword(PasswordHash nextPasswordHash) {
        this.passwordHash = Objects.requireNonNull(nextPasswordHash, "新密码哈希");
        this.mustChangePassword = true;
    }
}
