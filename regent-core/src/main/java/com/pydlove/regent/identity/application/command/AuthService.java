package com.pydlove.regent.identity.application.command;

import com.pydlove.regent.identity.application.query.AuthSessionGrant;
import com.pydlove.regent.identity.application.query.AuthenticatedUser;
import com.pydlove.regent.identity.application.query.CurrentAuthSession;
import com.pydlove.regent.identity.domain.model.ActiveAuthSession;
import com.pydlove.regent.identity.domain.model.AuthSession;
import com.pydlove.regent.identity.domain.model.Email;
import com.pydlove.regent.identity.domain.model.PasswordHash;
import com.pydlove.regent.identity.domain.model.SessionTokenHash;
import com.pydlove.regent.identity.domain.model.User;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.identity.domain.model.Username;
import com.pydlove.regent.identity.domain.port.PasswordHasher;
import com.pydlove.regent.identity.domain.port.SessionTokenCodec;
import com.pydlove.regent.identity.domain.repository.AuthSessionRepository;
import com.pydlove.regent.identity.domain.repository.UserRepository;
import com.pydlove.regent.shared.domain.AuthenticationException;
import com.pydlove.regent.shared.domain.DomainException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** 注册、登录与会话校验的统一用例入口。 */
public class AuthService {

    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 128;
    private static final int MAX_SESSION_TOKEN_LENGTH = 128;

    private final UserRepository userRepository;
    private final AuthSessionRepository sessionRepository;
    private final PasswordHasher passwordHasher;
    private final SessionTokenCodec tokenCodec;
    private final EmailVerificationService emailVerificationService;
    private final Clock clock;
    private final Duration sessionTtl;
    private final Duration rememberedSessionTtl;

    public AuthService(UserRepository userRepository,
                       AuthSessionRepository sessionRepository,
                       PasswordHasher passwordHasher,
                       SessionTokenCodec tokenCodec,
                       EmailVerificationService emailVerificationService,
                       Clock clock,
                       Duration sessionTtl,
                       Duration rememberedSessionTtl) {
        this.userRepository = Objects.requireNonNull(userRepository, "用户仓储");
        this.sessionRepository = Objects.requireNonNull(sessionRepository, "会话仓储");
        this.passwordHasher = Objects.requireNonNull(passwordHasher, "密码编码器");
        this.tokenCodec = Objects.requireNonNull(tokenCodec, "会话令牌编解码器");
        this.emailVerificationService = Objects.requireNonNull(
                emailVerificationService, "邮箱验证码服务");
        this.clock = Objects.requireNonNull(clock, "时钟");
        this.sessionTtl = Objects.requireNonNull(sessionTtl, "会话有效期");
        this.rememberedSessionTtl = Objects.requireNonNull(rememberedSessionTtl, "保持登录有效期");
        if (sessionTtl.isZero() || sessionTtl.isNegative()) {
            throw new DomainException("会话有效期必须大于零");
        }
        if (rememberedSessionTtl.isZero() || rememberedSessionTtl.isNegative()) {
            throw new DomainException("保持登录有效期必须大于零");
        }
        if (rememberedSessionTtl.compareTo(sessionTtl) < 0) {
            throw new DomainException("保持登录有效期不能短于普通会话有效期");
        }
    }

    public void sendRegistrationCode(String email) {
        emailVerificationService.sendRegistrationCode(email);
    }

    public AuthenticatedUser register(RegisterEmailCommand command) {
        Objects.requireNonNull(command, "注册命令");
        Email email = Email.of(command.email());
        requirePasswordPolicy(command.password());
        if (!command.password().equals(command.confirmPassword())) {
            throw new DomainException("两次输入的密码不一致");
        }
        if (userRepository.existsByEmail(email)) {
            throw new DomainException("该邮箱已注册，请直接登录");
        }

        Username username = allocateUsername(email);
        emailVerificationService.verifyRegistrationCode(email, command.emailCode());
        Instant now = clock.instant();
        User user = User.register(email, username, passwordHasher.hash(command.password()), now);
        userRepository.save(user);
        return view(user);
    }

    public AuthSessionGrant login(LoginCommand command) {
        Objects.requireNonNull(command, "登录命令");
        if (command.identifier() == null || command.identifier().isBlank()
                || command.password() == null || command.password().isEmpty()) {
            throw invalidCredentials();
        }

        User user;
        try {
            String identifier = command.identifier().trim();
            user = identifier.contains("@")
                    ? userRepository.findByEmail(Email.of(identifier)).orElseThrow(AuthService::invalidCredentials)
                    : userRepository.findByUsername(Username.of(identifier))
                            .orElseThrow(AuthService::invalidCredentials);
        } catch (DomainException ignored) {
            throw invalidCredentials();
        }
        if (!passwordHasher.matches(command.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }

        Instant now = clock.instant();
        Duration ttl = command.rememberMe() ? rememberedSessionTtl : sessionTtl;
        Instant expiresAt = now.plus(ttl);
        String rawToken = tokenCodec.generateRawToken();
        AuthSession session = AuthSession.open(
                user.getId(), tokenCodec.hash(rawToken), now, expiresAt, command.rememberMe());
        sessionRepository.save(session);
        return new AuthSessionGrant(view(user), rawToken, expiresAt, command.rememberMe());
    }

    /**
     * 给当前设备代理签发长期会话。设备代理不保存用户密码，只持有这个可撤销的 Bearer 令牌。
     */
    public AuthSessionGrant openDeviceSession(UserId userId, Duration ttl) {
        Objects.requireNonNull(userId, "用户 ID");
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new DomainException("设备会话有效期必须大于零");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthenticationException("用户不存在，无法创建设备会话"));
        Instant now = clock.instant();
        Instant expiresAt = now.plus(ttl);
        String rawToken = tokenCodec.generateRawToken();
        AuthSession session = AuthSession.open(
                user.getId(), tokenCodec.hash(rawToken), now, expiresAt, true);
        sessionRepository.save(session);
        return new AuthSessionGrant(view(user), rawToken, expiresAt, true);
    }

    public AuthenticatedUser currentUser(String rawToken) {
        ActiveAuthSession active = findActiveSession(rawToken, clock.instant())
                .orElseThrow(() -> new AuthenticationException("登录已失效，请重新登录"));
        User user = userRepository.findById(active.principal().userId())
                .orElseThrow(() -> new AuthenticationException("登录已失效，请重新登录"));
        return view(user);
    }

    public CurrentAuthSession currentSession(String rawToken) {
        Instant now = clock.instant();
        ActiveAuthSession active = findActiveSession(rawToken, now)
                .orElseThrow(() -> new AuthenticationException("登录已失效，请重新登录"));
        AuthSession session = active.session();
        if (session.renewIfNeeded(now, rememberedSessionTtl)) {
            sessionRepository.update(session);
        }
        User user = userRepository.findById(active.principal().userId())
                .orElseThrow(() -> new AuthenticationException("登录已失效，请重新登录"));
        return new CurrentAuthSession(
                view(user), session.getExpiresAt(), session.isRemembered());
    }

    public Optional<AuthenticatedUser> authenticate(String rawToken) {
        return findActiveSession(rawToken, clock.instant())
                .flatMap(active -> userRepository.findById(active.principal().userId())
                        .map(AuthenticatedUser::from));
    }

    private Optional<ActiveAuthSession> findActiveSession(String rawToken, Instant now) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > MAX_SESSION_TOKEN_LENGTH) {
            return Optional.empty();
        }
        SessionTokenHash tokenHash;
        try {
            tokenHash = tokenCodec.hash(rawToken);
        } catch (DomainException ignored) {
            return Optional.empty();
        }
        return sessionRepository.findActive(tokenHash, now);
    }

    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > MAX_SESSION_TOKEN_LENGTH) {
            return;
        }
        try {
            sessionRepository.deleteByTokenHash(tokenCodec.hash(rawToken));
        } catch (DomainException ignored) {
            // 无效 Cookie 不构成登出失败；客户端要的结果是“现在没有会话”。
        }
    }

    private static AuthenticatedUser view(User user) {
        return AuthenticatedUser.from(user);
    }

    private static AuthenticationException invalidCredentials() {
        return new AuthenticationException("账号或密码错误");
    }

    private Username allocateUsername(Email email) {
        String base = email.localPart().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]+", "-")
                .replaceAll("^-+|-+$", "");
        if (base.isBlank()) {
            base = "user";
        } else if (base.length() < 3) {
            base = "user-" + base;
        }
        base = truncate(base, 24);

        Username first = Username.of(base);
        if (!userRepository.existsByUsername(first)) {
            return first;
        }
        for (int suffix = 2; suffix < 10_000; suffix++) {
            String tail = "-" + suffix;
            Username candidate = Username.of(truncate(base, 32 - tail.length()) + tail);
            if (!userRepository.existsByUsername(candidate)) {
                return candidate;
            }
        }
        throw new DomainException("无法为该邮箱生成可用账号，请更换邮箱重试");
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
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
