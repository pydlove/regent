package com.pydlove.regent.identity.interfaces.rest;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.pydlove.regent.identity.application.command.AuthService;
import com.pydlove.regent.identity.application.command.EmailVerificationService;
import com.pydlove.regent.identity.application.command.LoginCommand;
import com.pydlove.regent.identity.application.command.RegisterEmailCommand;
import com.pydlove.regent.identity.application.query.AuthSessionGrant;
import com.pydlove.regent.identity.application.query.AuthenticatedUser;
import com.pydlove.regent.identity.domain.model.AuthPrincipal;
import com.pydlove.regent.identity.domain.model.AuthSession;
import com.pydlove.regent.identity.domain.model.Email;
import com.pydlove.regent.identity.domain.model.EmailVerificationCode;
import com.pydlove.regent.identity.domain.model.EmailVerificationCodeHash;
import com.pydlove.regent.identity.domain.model.PasswordHash;
import com.pydlove.regent.identity.domain.model.SessionTokenHash;
import com.pydlove.regent.identity.domain.model.User;
import com.pydlove.regent.identity.domain.model.Username;
import com.pydlove.regent.identity.domain.port.PasswordHasher;
import com.pydlove.regent.identity.domain.port.SessionTokenCodec;
import com.pydlove.regent.identity.domain.port.EmailVerificationCodec;
import com.pydlove.regent.identity.domain.port.VerificationEmailSender;
import com.pydlove.regent.identity.domain.repository.AuthSessionRepository;
import com.pydlove.regent.identity.domain.repository.EmailVerificationCodeRepository;
import com.pydlove.regent.identity.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {

    private StubAuthService authService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authService = new StubAuthService();
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService, false))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(
                        Jackson2ObjectMapperBuilder.json()
                                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                                .build()))
                .build();
    }

    @Test
    @DisplayName("邮箱注册只返回公开用户字段")
    void registerReturnsPublicUser() throws Exception {
        authService.registeredUser = new AuthenticatedUser(7, "alice", "alice@example.com");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"alice@example.com","emailCode":"123456","password":"safe-pass-123","confirmPassword":"safe-pass-123"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @DisplayName("发送邮箱验证码不返回敏感内容")
    void sendsEmailCodeWithoutResponseBody() throws Exception {
        mockMvc.perform(post("/api/auth/email-codes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"alice@example.com"}
                                """))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("登录把会话令牌写进 HttpOnly Cookie，不写进 JSON")
    void loginUsesHttpOnlyCookie() throws Exception {
        authService.grant = new AuthSessionGrant(
                new AuthenticatedUser(7, "alice", "alice@example.com"),
                "raw-session-token",
                Instant.now().plusSeconds(3600));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"alice","password":"safe-pass-123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie",
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("regent_session=raw-session-token"),
                                org.hamcrest.Matchers.containsString("HttpOnly"),
                                org.hamcrest.Matchers.containsString("SameSite=Lax"))))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.rawToken").doesNotExist());
    }

    private static final class StubAuthService extends AuthService {

        private AuthenticatedUser registeredUser;
        private AuthSessionGrant grant;
        private String sentEmail;

        private StubAuthService() {
            super(
                    new EmptyUserRepository(),
                    new EmptySessionRepository(),
                    new NoopPasswordHasher(),
                    new NoopTokenCodec(),
                    noopVerificationService(),
                    Clock.systemUTC(),
                    Duration.ofHours(1));
        }

        @Override
        public void sendRegistrationCode(String email) {
            sentEmail = email;
        }

        @Override
        public AuthenticatedUser register(RegisterEmailCommand command) {
            return registeredUser;
        }

        @Override
        public AuthSessionGrant login(LoginCommand command) {
            return grant;
        }
    }

    private static final class EmptyUserRepository implements UserRepository {

        @Override
        public boolean existsByUsername(Username username) {
            return false;
        }

        @Override
        public boolean existsByEmail(Email email) {
            return false;
        }

        @Override
        public Optional<User> findByUsername(Username username) {
            return Optional.empty();
        }

        @Override
        public Optional<User> findByEmail(Email email) {
            return Optional.empty();
        }

        @Override
        public void save(User user) {
        }
    }

    private static final class EmptySessionRepository implements AuthSessionRepository {

        @Override
        public void save(AuthSession session) {
        }

        @Override
        public Optional<AuthPrincipal> findActivePrincipal(SessionTokenHash tokenHash, Instant now) {
            return Optional.empty();
        }

        @Override
        public void deleteByTokenHash(SessionTokenHash tokenHash) {
        }
    }

    private static final class EmptyEmailCodeRepository implements EmailVerificationCodeRepository {

        private final Map<Long, EmailVerificationCode> codes = new HashMap<>();

        @Override
        public Optional<EmailVerificationCode> findActive(Email email, Instant now) {
            return codes.values().stream()
                    .filter(code -> code.getEmail().equals(email) && code.isActive(now))
                    .max(Comparator.comparing(EmailVerificationCode::getId));
        }

        @Override
        public Optional<Instant> findLatestCreatedAt(Email email) {
            return codes.values().stream()
                    .filter(code -> code.getEmail().equals(email))
                    .map(EmailVerificationCode::getCreatedAt)
                    .max(Comparator.naturalOrder());
        }

        @Override
        public long countCreatedSince(Email email, Instant since) {
            return 0;
        }

        @Override
        public void save(EmailVerificationCode code) {
            if (code.getId() == null) {
                code.assignIdentity((long) (codes.size() + 1));
            }
            codes.put(code.getId(), code);
        }
    }

    private static final class NoopEmailCodec implements EmailVerificationCodec {

        @Override
        public String generateCode() {
            return "123456";
        }

        @Override
        public EmailVerificationCodeHash hash(String rawCode) {
            return new EmailVerificationCodeHash("0".repeat(64));
        }
    }

    private static final class NoopEmailSender implements VerificationEmailSender {

        @Override
        public void sendRegistrationCode(Email email, String code, java.time.Duration ttl) {
        }
    }

    private static final class NoopPasswordHasher implements PasswordHasher {

        @Override
        public PasswordHash hash(String rawPassword) {
            return new PasswordHash("unused");
        }

        @Override
        public boolean matches(String rawPassword, PasswordHash encodedPassword) {
            return false;
        }
    }

    private static final class NoopTokenCodec implements SessionTokenCodec {

        @Override
        public String generateRawToken() {
            return "unused";
        }

        @Override
        public SessionTokenHash hash(String rawToken) {
            return new SessionTokenHash("0".repeat(64));
        }
    }

    private static EmailVerificationService noopVerificationService() {
        return new EmailVerificationService(
                new EmptyUserRepository(),
                new EmptyEmailCodeRepository(),
                new NoopEmailCodec(),
                new NoopEmailSender(),
                Clock.systemUTC(),
                Duration.ofMinutes(5),
                Duration.ofSeconds(60),
                10);
    }
}
