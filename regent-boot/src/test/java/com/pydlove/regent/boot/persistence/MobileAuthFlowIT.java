package com.pydlove.regent.boot.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pydlove.regent.identity.domain.port.PasswordHasher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MobileAuthFlowIT {

    private static final long USER_ID = 998_101L;
    private static final String USERNAME = "mobile_flow";
    private static final String PASSWORD = "mobile-flow-password";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void prepareUser() {
        jdbcTemplate.update("""
                INSERT INTO app_user (id, username, display_name, password_hash, email, created_at)
                VALUES (?, ?, '手机流程', ?, 'mobile-flow@example.com', UTC_TIMESTAMP(3))
                ON DUPLICATE KEY UPDATE password_hash = VALUES(password_hash)
                """, USER_ID, USERNAME, passwordHasher.hash(PASSWORD).value());
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("""
                DELETE h
                FROM mobile_refresh_token_history h
                JOIN mobile_auth_session s ON s.id = h.session_id
                WHERE s.user_id = ?
                """, USER_ID);
        jdbcTemplate.update("DELETE FROM mobile_auth_session WHERE user_id = ?", USER_ID);
        jdbcTemplate.update("DELETE FROM app_user WHERE id = ?", USER_ID);
    }

    @Test
    @DisplayName("手机登录、Bearer 访问、刷新轮换与旧令牌重放撤销形成闭环")
    void completesMobileAuthLifecycle() throws Exception {
        JsonNode first = login();
        String firstAccess = first.get("accessToken").asText();
        String firstRefresh = first.get("refreshToken").asText();

        mockMvc.perform(get("/api/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + firstAccess))
                .andExpect(status().isOk());

        JsonNode second = refresh(firstRefresh);
        String secondAccess = second.get("accessToken").asText();
        JsonNode third = refresh(second.get("refreshToken").asText());
        String thirdAccess = third.get("accessToken").asText();

        mockMvc.perform(get("/api/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + firstAccess))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + secondAccess))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + thirdAccess))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/mobile/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(firstRefresh)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + thirdAccess))
                .andExpect(status().isUnauthorized());
    }

    private JsonNode login() throws Exception {
        String response = mockMvc.perform(post("/api/mobile/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"mobile_flow","password":"mobile-flow-password","installationId":"install-flow-0001"}
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response);
    }

    private JsonNode refresh(String refreshToken) throws Exception {
        String response = mockMvc.perform(post("/api/mobile/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(refreshToken)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response);
    }
}
