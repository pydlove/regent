package com.pydlove.regent.identity.application.command;

import com.pydlove.regent.identity.domain.model.DisplayName;
import com.pydlove.regent.identity.domain.model.Email;
import com.pydlove.regent.identity.domain.model.User;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.identity.domain.model.Username;
import com.pydlove.regent.identity.domain.port.PasswordHasher;
import com.pydlove.regent.identity.domain.repository.UserRepository;
import com.pydlove.regent.shared.domain.DomainException;

import java.time.Clock;
import java.util.Objects;

/**
 * 受信任的账号创建入口。普通注册仍走邮箱验证码；本服务只供组织邀请等已由管理员授权的场景使用。
 */
public class UserProvisioningService {

    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 128;

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final Clock clock;

    public UserProvisioningService(UserRepository userRepository,
                                   PasswordHasher passwordHasher,
                                   Clock clock) {
        this.userRepository = Objects.requireNonNull(userRepository, "用户仓储");
        this.passwordHasher = Objects.requireNonNull(passwordHasher, "密码编码器");
        this.clock = Objects.requireNonNull(clock, "时钟");
    }

    public User createInvited(Email email, DisplayName displayName, String rawPassword) {
        Objects.requireNonNull(email, "邀请邮箱");
        Objects.requireNonNull(displayName, "显示名称");
        requirePasswordPolicy(rawPassword);
        if (userRepository.existsByEmail(email)) {
            throw new DomainException("该邮箱已注册，请登录后接受邀请");
        }
        Username username = UsernameAllocator.allocate(userRepository, email);
        User user = User.register(
                email, username, passwordHasher.hash(rawPassword), clock.instant());
        user.updateDisplayName(displayName);
        userRepository.save(user);
        return user;
    }

    /** 管理员手动创建账号：初始密码只用于首次登录，登录后必须修改。 */
    public User createTemporary(Email email,
                                DisplayName displayName,
                                String initialPassword) {
        Objects.requireNonNull(email, "账号邮箱");
        Objects.requireNonNull(displayName, "显示名称");
        requirePasswordPolicy(initialPassword);
        if (userRepository.existsByEmail(email)) {
            throw new DomainException("该邮箱已注册，请直接为现有账号分配组织角色");
        }
        Username username = UsernameAllocator.allocate(userRepository, email);
        User user = User.registerTemporary(
                email,
                username,
                displayName,
                passwordHasher.hash(initialPassword),
                clock.instant());
        userRepository.save(user);
        return user;
    }

    /** 组织管理员重置成员密码；只改密码并重新要求首次登录改密，不覆盖账号资料。 */
    public User resetTemporaryPassword(UserId userId, String initialPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new DomainException("账号不存在"));
        requirePasswordPolicy(initialPassword);
        user.resetTemporaryPassword(passwordHasher.hash(initialPassword));
        userRepository.update(user);
        return user;
    }

    private static void requirePasswordPolicy(String password) {
        if (password == null) {
            throw new DomainException("密码不能为空");
        }
        int length = password.codePointCount(0, password.length());
        if (length < MIN_PASSWORD_LENGTH) {
            throw new DomainException("密码至少需要 " + MIN_PASSWORD_LENGTH + " 个字符");
        }
        if (length > MAX_PASSWORD_LENGTH) {
            throw new DomainException("密码不能超过 " + MAX_PASSWORD_LENGTH + " 个字符");
        }
    }
}
