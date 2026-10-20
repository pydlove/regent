package com.pydlove.regent.boot.persistence;

import com.jayway.jsonpath.JsonPath;
import com.pydlove.regent.identity.application.command.AuthService;
import com.pydlove.regent.identity.application.command.LoginCommand;
import com.pydlove.regent.identity.application.command.RegisterEmailCommand;
import com.pydlove.regent.identity.application.query.AuthSessionGrant;
import com.pydlove.regent.identity.domain.model.Email;
import com.pydlove.regent.identity.domain.model.EmailVerificationCode;
import com.pydlove.regent.identity.domain.port.EmailVerificationCodec;
import com.pydlove.regent.identity.domain.repository.EmailVerificationCodeRepository;
import com.pydlove.regent.shared.domain.DomainException;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 认证链路在真实 MySQL 上的接缝：Flyway V12、唯一键、摘要落库以及 HTTP 会话守卫。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthPersistenceIT {

    @Autowired
    private AuthService authService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EmailVerificationCodeRepository emailCodeRepository;

    @Autowired
    private EmailVerificationCodec emailVerificationCodec;

    @Autowired
    private MockMvc mockMvc;

    private static final String EMAIL_CODE = "123456";

    @Test
    @DisplayName("注册与登录只落密码和会话摘要，明文不进入数据库")
    void storesOnlyPasswordAndSessionDigests() {
        String email = email("digest");
        String password = "safe-pass-123";

        seedEmailCode(email);
        var registered = authService.register(new RegisterEmailCommand(email, EMAIL_CODE, password, password));
        AuthSessionGrant grant = authService.login(new LoginCommand(email, password, false));

        String passwordHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM app_user WHERE email = ?", String.class, email);
        assertTrue(passwordHash.startsWith("pbkdf2-sha256$310000$"));
        assertNotEquals(password, passwordHash);

        String tokenHash = jdbcTemplate.queryForObject(
                "SELECT token_hash FROM auth_session WHERE user_id = "
                        + "(SELECT id FROM app_user WHERE email = ?)",
                String.class,
                email);
        assertEquals(64, tokenHash.length());
        assertNotEquals(grant.rawToken(), tokenHash);
        assertEquals(email, registered.email());
        assertEquals(email, authService.currentUser(grant.rawToken()).email());
    }

    @Test
    @DisplayName("保持登录标记与会话有效期正确落库")
    void persistsRememberedSession() {
        String email = email("remembered");
        String password = "safe-pass-123";
        seedEmailCode(email);
        authService.register(new RegisterEmailCommand(email, EMAIL_CODE, password, password));

        AuthSessionGrant grant = authService.login(new LoginCommand(email, password, true));

        Boolean remembered = jdbcTemplate.queryForObject(
                "SELECT remembered FROM auth_session WHERE user_id = "
                        + "(SELECT id FROM app_user WHERE email = ?)",
                Boolean.class,
                email);
        assertEquals(Boolean.TRUE, remembered);
        assertTrue(grant.remembered());
        assertTrue(grant.expiresAt().isAfter(Instant.now().plus(Duration.ofDays(179))));
    }

    @Test
    @DisplayName("邮箱大小写不敏感，数据库唯一键兜住并发重复注册")
    void rejectsCaseInsensitiveDuplicateEmail() {
        String email = email("duplicate");
        seedEmailCode(email);
        authService.register(new RegisterEmailCommand(email, EMAIL_CODE, "safe-pass-123", "safe-pass-123"));

        assertThrows(DomainException.class,
                () -> authService.register(new RegisterEmailCommand(
                        email.toUpperCase(), EMAIL_CODE, "safe-pass-456", "safe-pass-456")));
    }

    @Test
    @DisplayName("管理 API 无会话返回 401，登录后可访问且登出后立即失效")
    void protectsManagementApiWithHttpOnlySession() throws Exception {
        String email = email("http");
        String password = "safe-pass-123";
        seedEmailCode(email);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","emailCode":"%s","password":"%s","confirmPassword":"%s"}
                                """.formatted(email, EMAIL_CODE, password, password)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email));

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rawToken").doesNotExist())
                .andReturn();

        Cookie session = login.getResponse().getCookie("regent_session");
        assertTrue(session.isHttpOnly());
        mockMvc.perform(get("/api/tasks/stats"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/tasks/stats").cookie(session))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/auth/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));

        mockMvc.perform(post("/api/auth/logout").cookie(session))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/auth/me").cookie(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("设备代理用 Bearer 令牌访问设备接口，明文令牌不落库")
    void deviceAgentUsesBearerSession() throws Exception {
        String email = email("device");
        String password = "safe-pass-123";
        seedEmailCode(email);
        authService.register(new RegisterEmailCommand(email, EMAIL_CODE, password, password));

        AuthSessionGrant login = authService.login(new LoginCommand(email, password, true));
        Cookie session = new Cookie("regent_session", login.rawToken());
        MvcResult device = mockMvc.perform(post("/api/auth/device-session").cookie(session))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isString())
                .andReturn();
        String token = JsonPath.read(
                device.getResponse().getContentAsString(), "$.token");

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));

        String stored = jdbcTemplate.queryForObject(
                "SELECT token_hash FROM auth_session WHERE user_id = "
                        + "(SELECT id FROM app_user WHERE email = ?) ORDER BY id DESC LIMIT 1",
                String.class,
                email);
        assertEquals(64, stored.length());
        assertNotEquals(token, stored);
    }

    private void seedEmailCode(String email) {
        Email address = Email.of(email);
        Instant now = Instant.now();
        emailCodeRepository.findActive(address, now).ifPresent(active -> {
            active.invalidate(now);
            emailCodeRepository.save(active);
        });
        emailCodeRepository.save(EmailVerificationCode.issue(
                address,
                emailVerificationCodec.hash(EMAIL_CODE),
                now,
                Duration.ofMinutes(5)));
    }

    private static String email(String prefix) {
        return "it_" + prefix + "_"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 12)
                + "@example.com";
    }
}
